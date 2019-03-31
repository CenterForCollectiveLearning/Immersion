import math
import time
import tornado
import tornado.ioloop
import tornado.web
import tornado.escape
import tornado.log
import tornado.options
from oauth2client.client import flow_from_clientsecrets
from oauth2client.multistore_file import get_credential_storage
import json
import string
import random
import sys
import httplib2
import os
import os.path
from tornado.web import URLSpec
import db
import base64
from datetime import datetime
from tornado.websocket import WebSocketHandler
import base64
import uuid
import emailer
import email.utils
import traceback
import logging
import pytz
from db import db as db_direct
import re

########### GENERIC HANDLERS ####################
#
# All handlers should extend the Basic handler
class BaseHandler(tornado.web.RequestHandler):
  def _prepare_context(self):
    self._context = {}
    self._context['debug'] = tornado.options.options.debug
    self._context['exhibit'] = tornado.options.options.exhibit

  def prepare(self):
    self._prepare_context()
    self.set_header("Access-Control-Allow-Origin", "*")
    self.set_header("Access-Control-Allow-Methods", "HEAD, GET, POST, PUT, DELETE")

  def get_current_user(self):
    return self.get_secure_cookie("email")

  def get_current_study(self):
    return self.get_secure_cookie("studyid")

  def get_authid(self):
    return self.get_secure_cookie("authrequestid")

  def unset_authid(self):
    self.clear_cookie("authrequestid")

  def set_authid(self, authid):
    self.set_secure_cookie("authrequestid", authid)

  def render_string(self, template_name, **kwargs):
    """Override default render_string, add context to template."""
    assert "context" not in kwargs, "context is a reserved word for \
            template context valuable."
    kwargs['context'] = self._context

    return super(BaseHandler, self).render_string(template_name, **kwargs)

  def stream_file(self,f):
    while True:
      data = f.read(1024*1024) # 1MB at a time
      if data == "": break
      self.write(data)
      self.flush()

  def get_api_response(self, credentials, uri):
    http = credentials.authorize(httplib2.Http())
    response, content = http.request(uri)
    content = json.loads(content)
    return content

# Handlers that require user to be logged in should extent this handler
class LoggedInRequiredHandler(BaseHandler):
  def prepare(self):
    super(LoggedInRequiredHandler, self).prepare()
    if self.current_user is None: self.redirect(self.reverse_url('index'))

# Handlers that require user to have auth request should extent this handler
class AuthRequestRequiredHandler(BaseHandler):
  def prepare(self):
    super(AuthRequestRequiredHandler, self).prepare()
    if self.get_authid() is None: self.redirect(self.reverse_url('index'))

########## END GENERIC HANDLERS ###############################

########### ALL HANDLERS BEGIN HERE ###############
class AuthorizerReturned(BaseHandler):
  def get(self):
    try:
      redirect_uri = self.request.protocol + "://" + self.request.host + "/" + client_info['web']['redirect_uris'][0]
      flow = flow_from_clientsecrets('client_secrets.json', scope=client_info['web']['scope'], redirect_uri=redirect_uri)
      code = self.get_argument("code", None)
      if code is None:
        db.log(email=None, ip=self.request.remote_ip, module="AuthorizerReturned", msg='rejected immersion')
        self.redirect(self.reverse_url('index'))
        return
      studyid = self.get_current_study()
      credentials = flow.step2_exchange(code)
      uri = "https://www.googleapis.com/oauth2/v2/userinfo/?alt=json" # user info uri
      userinfo = self.get_api_response(credentials, uri)
      email = userinfo['email']
      # we practically have the email now

      # store refresh token
      state = db.getState(email, studyid)

      if state is None:
        state = {'email': email, 'userinfo' : userinfo, 'lastuid': 0, 'version': -1} # new user
        if studyid: state['studyid'] = studyid

      # backward compatibility, store userinfo again anyway
      state['userinfo'] = userinfo

      # always store the new credentials
      state['credentials'] = credentials.to_json()
      db.storeState(email, studyid, state)

      # we store a secure cookie to remember that user is logged in
      self.set_secure_cookie("email", email)

      # only add if there is no other task in the queue for the same person
      if not db.hasTask(email, studyid):
        db.pushTask(email, studyid)
        print 'Pushtask with', studyid
        db.log(email=email, ip=self.request.remote_ip, module="AuthorizerReturned", msg='Added a fetching task')

      self.redirect(self.reverse_url('viz'))
    except:
      db.log(email=None, ip=self.request.remote_ip, module="AuthorizerReturned", msg=traceback.format_exc(), level=logging.ERROR)
      self.redirect(self.reverse_url('busy'))


class IndexStudy(BaseHandler):
  def get(self, studyid):
    # remember the studyid for the user session
    if studyid: self.set_secure_cookie("studyid", studyid)

    form_email = self.get_secure_cookie("form_email")
    if form_email is None: form_email = ''
    form_username = self.get_secure_cookie("form_username")
    if form_username is None: form_username = ''
    study = db.getStudy(studyid)
    if study == None: raise tornado.web.HTTPError(404)
    self.render('indexStudy.html', form_email=form_email, form_username=form_username, study=study)

class ServeValidation(BaseHandler):
  def get(self):
    f = open('/home/djagdish/dev/newserver/65DA6E28F7B97BC0D7D44DA4817335D2.txt')
    self.set_header("Content-Type", 'text/plain; charset="utf-8"')
    self.write(f.read())
    f.close()

class Index(BaseHandler):
  def get(self):
    studyid = self.get_current_study()
    if studyid: self.redirect(self.reverse_url('indexStudy', studyid))
    #if self.request.protocol == 'http': self.redirect("https://" + self.request.host) # always redirect to secure traffic
    #redirect_uri = "https://" + self.request.host + "/" + client_info['web']['redirect_uris'][0]
    #flow = flow_from_clientsecrets('client_secrets.json', scope=client_info['web']['scope'], redirect_uri=redirect_uri)
    #authurl = flow.step1_get_authorize_url()#.replace('access_type=offline', 'access_type=online')
    form_email = self.get_secure_cookie("form_email")
    if form_email is None: form_email = ''
    form_username = self.get_secure_cookie("form_username")
    if form_username is None: form_username = ''
    self.render('index.html', form_email=form_email, form_username=form_username)

class SubmitFeedback(LoggedInRequiredHandler):
  def post(self):
    text = self.get_argument("feedback_text")
    db.submitFeedback(self.current_user, text, self.request.remote_ip)

class SendError(LoggedInRequiredHandler):
  def post(self):
    error  = self.get_argument("json", None)
    if error is None: return
    error = json.loads(error)
    db.storeError(self.current_user, error)

class SendStats(LoggedInRequiredHandler):
  def post(self):
    stats  = self.get_argument("json", None)
    if stats is None: return
    stats = json.loads(stats)
    db.storeStats(self.current_user, stats)

class GetStats(LoggedInRequiredHandler):
  def get(self):

    ncollaborators = db.get("ncollaborators")
    nsent = db.get("nsent")
    nrcv = db.get("nrcv")
    obj = {'ncollaborators': ncollaborators, 'nsent' : nsent, 'nrcv' : nrcv }
    self.set_header('Content-Type', 'application/json')
    self.set_header('Content-Encoding','gzip')
    content = db.jsonToGzip(obj)
    self.write(content)

class AddToWaitlist(LoggedInRequiredHandler):
  def post(self):
    waitlist_email = self.get_argument("waitlist_email")
    db.waitlisting(waitlist_email, self.request.remote_ip)

class LogoutDelete(BaseHandler):
  def get(self):
    self.render('logout_delete.html')

class LogoutSave(BaseHandler):
  def get(self):
    self.render('logout_save.html')

class ImapPage(BaseHandler):
  def get(self):
    self.render('gmail/imap.html')

class BusyPage(BaseHandler):
  def get(self):
    self.render('gmail/busy.html')

class PrivacyPolicy(BaseHandler):
  def get(self):
    self.render('privacy.html')

class Logout(LoggedInRequiredHandler):
  def get(self):
    email = self.current_user
    studyid = self.get_current_study()
    if email:
      self.clear_cookie("email")
      if email == 'demo@demo.com' or studyid:
        if studyid: self.clear_cookie("studyid")
        self.redirect('/')
        return
      if self.get_argument('delete', False):
        db.deleteData(email, studyid)
        db.log(email=email, ip=self.request.remote_ip, module="Logout", msg="logged out and deleted data")
        self.redirect(self.reverse_url('logout_delete'))
      else:
        db.log(email=email, ip=self.request.remote_ip, module="Logout", msg="logged out and saved data")
        self.redirect(self.reverse_url('logout_save'))
    # don't logout from google for the web app
    else: self.redirect('/')


class Viz(LoggedInRequiredHandler):
    def get(self):
      if self.current_user is None: self.redirect(self.reverse_url('index'))
      studyid = self.get_current_study()

      # comment the next lines and uncomment the next section for testing
      state = db.getState(self.current_user, studyid)
      if state is None or 'userinfo' not in state:
        self.redirect(self.reverse_url('index'))
        return

      ################################################
      #email = db.db.states.find().skip(random.randint(0, db.db.states.count() - 1)).limit(1)[0]['email']
      #email = 'dsmilkov@gmail.com'
      #state = db.getState(email, None)
      #if 'userinfo' not in state: state['userinfo'] = db.getState("dsmilkov@gmail.com", None)['userinfo']
      #self.set_secure_cookie("email", email)
      #print email, state['version']
      ################################################

      working = 0
      if 'working' in state and state['working']: working = 1
      if 'imap' in state and state['imap']: self.redirect(self.reverse_url('imap'))

      waittime = 1
      time = db.getTaskTimeAheadofQueue()
      if time is not None:
        now = datetime.now(pytz.UTC)
        if now > time:
          delta = now - time
          waittime = int(math.ceil(delta.seconds/60.0)) + 1
      if studyid == None: studyid = 0
      self.render('viz.html', version=state['version'], userinfo = json.dumps(state['userinfo']), working=working, waittime=waittime, studyid=studyid)
      
class Demo(BaseHandler):
    def get(self):
      # login as demo
      self.set_secure_cookie("email", "demo@demo.com")
      working = 0
      userinfo = {'name': 'Demo User', 'given_name':'Demo', 'family_name': 'User', 'email':'demo@demo.com'}
      self.render('viz.html', version=2, userinfo = json.dumps(userinfo), working=working, waittime=0, studyid=0)



class Snapshot(LoggedInRequiredHandler):
  def post(self):
    email = self.current_user
    try:
      if self.get_argument('b64', False):
        b64 = self.get_argument('b64')
        png = base64.decodestring(b64)

        if not os.path.exists('static/snapshots/'): os.mkdir("static/snapshots/")
        while True:
          filename = 'static/snapshots/' + str(uuid.uuid4()) + '.png'
          if not os.path.exists(filename): break

        f = open(filename, "w")
        f.write(png)
        f.close()
        # send email unless the user is logging out
        self.write({'success':True ,'url':  "https://" + self.request.host + '/' + filename})
      else:
        self.write({'success':False})
    except:
      self.write({'success':False})
      db.log(email=email, ip=self.request.remote_ip, module="Snapshot",
        msg=traceback.format_exc(), level=logging.ERROR)


class AuthPage(AuthRequestRequiredHandler):
  def get(self):
    authRequest = db.getAuthRequest(self.get_authid())
    if authRequest['status'] == db.AuthStatus.AUTHORIZED:
      # login the user with the email
      #
      self.set_secure_cookie("email", authRequest['email'])
      # remove the auth request info from the db
      db.removeAuthRequest(self.get_authid())
      # remove the auth request id from cookie
      self.unset_authid()
      # redirect to main visualization page
      self.redirect(self.reverse_url('viz'))
      return
    if authRequest['status'] == db.AuthStatus.FAILED:
      self.redirect(self.reverse_url('authfailed'))
      return
    if authRequest['status'] == db.AuthStatus.PENDING:
      self.render('auth/auth.html')
      return

class AuthFailedPage(BaseHandler):
  def get(self):
    self.render('auth/authfailed.html')

class AuthEWS(BaseHandler):
  def post(self):
    studyid = self.get_current_study()
    email = self.get_argument('email', None)
    username = self.get_argument('username', None)
    password = self.get_argument('password', None)
    if email is None or username is None or password is None or email.strip() == '' or username.strip() == '' or password.strip() == '':
      self.redirect(self.reverse_url('index'))
    email = email.lower().strip()

    self.set_secure_cookie("form_email", email)
    self.set_secure_cookie("form_username", username)
    domain = email[email.find('@')+1:]
    #if domain in ['exchange.com', 'hotmail.com', 'live.com']: emailType = db.EmailType.HOTMAIL
    #else: emailType = db.EmailType.EXCHANGE
    emailType = db.EmailType.EXCHANGE

    authid = db.insertAuthRequest(username, email, password, emailType, studyid)
    self.set_authid(authid)

    # only add if there is no other task in the queue for the same person
    if not db.hasTask(email, studyid):
      db.pushTask(email, studyid, authid=authid)
      print 'Pushtask with', studyid
      db.log(email=email, ip=self.request.remote_ip, module="AuthEWS", msg='Added a fetching task')
    self.redirect(self.reverse_url('auth'))


class AuthYahoo(BaseHandler):
  def post(self):
    studyid = self.get_current_study()
    email = self.get_argument('email', None)
    password = self.get_argument('password', None)
    if email is None or password is None or email.strip() == '' or password.strip() == '':
      self.redirect(self.reverse_url('index'))
    email = email.lower().strip()

    self.set_secure_cookie("form_email", email)
    domain = email[email.find('@')+1:]
    #if domain in ['exchange.com', 'hotmail.com', 'live.com']: emailType = db.EmailType.HOTMAIL
    #else: emailType = db.EmailType.EXCHANGE
    emailType = db.EmailType.YAHOO

    authid = db.insertAuthRequest(email, email, password, emailType, studyid)
    self.set_authid(authid)

    # only add if there is no other task in the queue for the same person
    if not db.hasTask(email, studyid):
      db.pushTask(email, studyid, authid=authid)
      print 'Pushtask with', studyid
      db.log(email=email, ip=self.request.remote_ip, module="AuthYahoo", msg='Added a fetching task')
    self.redirect(self.reverse_url('auth'))


class DownloadEmails(LoggedInRequiredHandler):
  def get(self):
    self.render('downloademails.html')

class DataDecision(BaseHandler):
  def get(self):
    self.render('datadecision.html')

class GetState(LoggedInRequiredHandler):
  def get(self):
    state = db.getStateJson(self.current_user, self.get_current_study())
    self.write(state)
    self.finish()

class GetEmails(LoggedInRequiredHandler):

  @tornado.web.asynchronous
  def get(self, version):
    studyid = self.get_current_study()
    modifiedTimestamp = db.getModifiedTimestamp(self.current_user, int(version), studyid)
    if modifiedTimestamp == None: raise tornado.web.HTTPError(404)
    contentLength = db.getContentLength(self.current_user, int(version), studyid)
    self.set_header("Content-Length", contentLength)
    self.set_header("Cache-Control", "no-cache")
    ifmodified = self.request.headers.get("If-Modified-Since", None)
    if ifmodified is not None:
      ifmodifiedTimestamp = email.utils.mktime_tz(email.utils.parsedate_tz(ifmodified))
      if int(modifiedTimestamp) == int(ifmodifiedTimestamp):
        self.set_status(304)
        self.finish()
        return

    content = db.getEmailsContent(self.current_user, int(version), studyid)
    if content is None: raise tornado.web.HTTPError(404)
    self.set_header("Last-Modified", email.utils.formatdate(modifiedTimestamp, usegmt=True))
    self.set_header('Content-Encoding','gzip')
    self.set_header('Content-Type', 'application/json')
    self.write(content)
    self.finish()


EMAIL_REGEX = re.compile(r"^[_a-z0-9-]+(\.[_a-z0-9-]+)*@[a-z0-9-]+(\.[a-z0-9-]+)*(\.[a-z]{2,4})$")
def isValidEmail(email):
  return EMAIL_REGEX.match(email.lower())

class CreateStudy(BaseHandler):
  def get(self):
    self.render('study/create.html')

  def post(self):
    name  = self.get_argument("study_name", '').strip()
    description  = self.get_argument("study_description", '').strip()
    email = self.get_argument("study_email", '').strip()
    email2 = self.get_argument("study_email2", '').strip()
    if name and description and email and email == email2 and isValidEmail(email):
      study = db.createStudy(name, description, email)
      self.write({'success':True, 'id': study['id'], 'password' : study['password']})
    else: self.write({'success':False})


settings = {
  "cookie_secret": 'whateverImmersion2013',
  "debug": False,
  "logging": False,
  #"log_function" : lambda x: 0,
  "static_path": os.path.join(os.path.dirname(__file__), "static"),
  "template_path": "templates"
}

tornado.options.define("debug", default=False, help="Running in DEBUG mode")
tornado.options.define("port", default=80, help="Port number")
tornado.options.define("exhibit", default=False, help="Running in exhibit mode")

tornado.options.parse_command_line()
settings['debug'] = tornado.options.options.debug


application = tornado.web.Application([
  URLSpec("/", Index, name='index'),
  URLSpec("/viz", Viz, name='viz'),
  URLSpec("/demo", Demo, name='demo'),
  URLSpec(r"/getemails/([0-9]+)", GetEmails),
  URLSpec("/logout", Logout),
  URLSpec("/snapshot", Snapshot),
  URLSpec("/feedback", SubmitFeedback),
  URLSpec("/waitlist", AddToWaitlist),
  URLSpec("/logoutdelete", LogoutDelete, name='logout_delete'),
  URLSpec("/logoutsave", LogoutSave, name="logout_save"),
  URLSpec("/imap", ImapPage, name="imap"),
  URLSpec("/busy", BusyPage, name="busy"),
  URLSpec("/getstate", GetState),
  URLSpec("/sendstats", SendStats),
  URLSpec("/getstats", GetStats),
  URLSpec("/senderror", SendError),
  URLSpec("/downloademails", DownloadEmails),
  URLSpec("/datadecision", DataDecision),
  URLSpec("/privacy", PrivacyPolicy),

  #### authorization pages ####
  URLSpec(r"/authews", AuthEWS),
  URLSpec(r"/authyahoo", AuthYahoo),
  URLSpec(r"/oauth2callback", AuthorizerReturned),
  URLSpec("/auth", AuthPage, name="auth"),
  URLSpec("/authfailed", AuthFailedPage, name='authfailed'),
  ##############################

  ### study pages #############
  URLSpec("/study/create", CreateStudy),
  URLSpec(r"/study/([A-Z0-9]+)/?", IndexStudy, name="indexStudy"),
  URLSpec("/65DA6E28F7B97BC0D7D44DA4817335D2.txt", ServeValidation),
], **settings)


### load the client_info ###
f = open('client_secrets.json', 'r')
client_info = json.load(f)
f.close()
tmp = client_info['web']['redirect_uris'][0]
server_uri = tmp[0:tmp.rfind('/') + 1]
###


def clearSnapshots():
  mypath = 'static/snapshots/'
  onlyfiles = [ f for f in os.listdir(mypath) if os.path.isfile(os.path.join(mypath,f)) ]
  print '# of snapshots on disk', len(onlyfiles)

  nremoved = 0
  gap = 30*24*60*60 # 30 days X 24 hours X 60 minutes X 60 seconds
  for f in onlyfiles:
    fpath = os.path.join(mypath,f)
    timestamp = os.path.getmtime(fpath)
    if time.time() - timestamp > gap:
      os.remove(fpath)
      print 'removed', f, 'created at', datetime.fromtimestamp(timestamp)
      nremoved += 1
  print '# of snapshots removed', nremoved

def aggregateStats():
  # number of collaborators
  ncollabs, nsent, nrcv = [],[],[]
  for stat in db_direct.statistics.find():
    if 'ncollaborators' in stat and stat['ncollaborators'] >= 10:
      ncollabs.append(stat['ncollaborators'])
    if 'nsent' in stat and stat['nsent'] > 100:
      nsent.append(stat['nsent'])
    if 'nrcv' in stat and stat['nrcv'] > 100:
      nrcv.append(stat['nrcv'])
  ncollabs.sort()
  nsent.sort()
  nrcv.sort()


  db.put('ncollaborators', ncollabs)
  db.put('nsent', nsent)
  db.put('nrcv', nrcv)


if __name__ == "__main__":
    application.listen(tornado.options.options.port, xheaders=True)
    #application.listen(port_num+1000, ssl_options = {
    #  "certfile": "immersion.crt",
    #  "keyfile": "immersion.key",
    #})
    if tornado.options.options.debug:
      db.log(email=None, ip=None, module="__main__", msg='running in debug mode')
    if tornado.options.options.exhibit:
      db.log(email=None, ip=None, module="__main__", msg='running in exhibit mode')
    db.log(email=None, ip=None, module="__main__", msg='starting on port %d' % tornado.options.options.port)
    try:
      # run aggregate stats every 10min
      tornado.ioloop.PeriodicCallback(aggregateStats, 10*60*1000).start()
      # run cleanup of snapshots every 6 hours
      tornado.ioloop.PeriodicCallback(clearSnapshots, 6*60*60*1000).start()
      # start the server
      tornado.ioloop.IOLoop.instance().start()
    except (KeyboardInterrupt, SystemExit):
      print "Someone called ctrl+c"
    except:
      raise
