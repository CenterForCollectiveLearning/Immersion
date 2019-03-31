import json
import os
import os.path
import shutil
import gzip
import logging
import cStringIO
import time
import sys
from pymongo import MongoClient
from bson.binary import Binary
import datetime
import pytz
import email.utils as eutils
import pymongo
from bson.objectid import ObjectId
import string
import random
import email as em
import traceback
from email.parser import HeaderParser
import calendar

class EmailType:
  GMAIL = 1
  EXCHANGE = 2
  HOTMAIL = 3
  YAHOO = 4

class AuthStatus:
  PENDING = 1
  AUTHORIZED = 2
  FAILED = 3

class MyEncoder(json.JSONEncoder):
    def default(self, obj):
        return obj.__dict__

try:
  db
except NameError:
  client = MongoClient(tz_aware=True)
  client.the_database.authenticate('immersion', 'immersion2018', source='immersion')
  db =client['immersion']
  db.states.ensure_index("email") # don't index by both email and studyid, rebuilding time is huge
  db.emails.ensure_index("email") # don't index by both email and studyid, rebuilding time is huge
  db.tasks.ensure_index('timestamp')
  db.tasks.ensure_index('email')
  db.statistics.ensure_index('email')
  db.notifications.ensure_index('email')
  db.errors.ensure_index('email')

try:
  db.create_collection("logs", capped=True, size=1024*1024*1024) # capped at 1GB for storing logs
except:
  pass

def getState(email, studyid):
  query = {"email": email}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  return db.states.find_one(query)


def getStateJson(email, studyid):
  state = getState(email, studyid)
  if state is None: return None
  del state['_id']
  #return json.dumps(state)
  return state
  #if state is None:
  #  state = {'email':email, 'lastuid':0, 'version': -1}
  #  db.states.insert(state)
  #return state


def id_generator(size=6, chars=string.ascii_uppercase + string.digits):
  return ''.join(random.choice(chars) for x in range(size))

def hasStudyID(study_id):
  return db.studies.find_one({'id' : study_id})

def createStudy(study_name, study_description, email):
  password = id_generator(size=10)
  while True:
    study_id = id_generator(size=6)
    if not hasStudyID(study_id): break
  study = {'name': study_name, 'description': study_description, 'id': study_id, 'email': email, 'password': password}
  db.studies.insert(study)
  return study

def getStudy(studyid):
  return db.studies.find_one({'id' : studyid})

def getUserInfo(email):
  tmp = db.states.find_one({"email": email}, {'userinfo':1})
  if 'userinfo' in tmp: return tmp['userinfo']

def storeState(email, studyid, state):
  query = {'email':email}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  db.states.update(query, state, upsert=True)

def storeStats(email, stats):
  stats['email'] = email
  stats['timestamp'] = datetime.datetime.now(pytz.UTC)
  db.statistics.update({'email':email}, stats, upsert=True)

def jsonToGzip(json_obj):
  output = cStringIO.StringIO()
  f = gzip.GzipFile(fileobj=output, mode='w')
  json_dump = json.dumps(json_obj)
  f.write(json_dump)
  f.close()
  contents = output.getvalue()
  output.close()
  return contents

def storeEmails(email, emails, version, studyid):
  version = int(version)
  contents = jsonToGzip(emails)
  query = {'email': email, 'version':version}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  
  obj = {'email': email, 'version':version, 'contents': Binary(contents),
    'timestamp': int(time.time()), 'length': len(contents)}
  if studyid: obj['studyid'] = studyid
  db.emails.update(query, obj, upsert=True)

def getItem(itemName, line1):
  startIdx = line1.find(itemName) + len(itemName) + 1
  if line1[startIdx] == '"':
    item = line1[startIdx+1 : line1.find('"', startIdx + 1)]
  else:
    item = line1[startIdx : line1.find(" ", startIdx)]
  return item

{
'fromField': ['Tricia Navarro (Twitter)', 'n-qfzvyxbi=tznvy.pbz-7a49a@postmaster.twitter.com'],
'toField': [['Daniel Smilkov', 'dsmilkov@gmail.com']],
'dateField': 1372743719,
'UID': '101451',
'isSent': False, 
'threadid': '1439426117975266137',
}


def parseHeader(line1, line2):
  header = {}
  msg = em.message_from_string(line2.encode("utf-8"), strict=False)
  fromStr = msg.get("from")
  if fromStr is None: return None
  fromName, fromAddr = em.utils.parseaddr(fromStr)
  if fromAddr is None: return None
  header['fromField'] = [fromName, fromAddr]
  header['toField'] = map(lambda x: (x[0], x[1]) ,em.utils.getaddresses(msg.get_all("to", []) + msg.get_all("cc", [])))
  # ==== parse the date ====
  try:
    if 'INTERNALDATE' in line1: # check if it is the new format
      header['dateField'] = calendar.timegm(eutils.parsedate(getItem('INTERNALDATE', line1)))
    else: # it is the old format with the date field
      if msg.get("date") is None: return None
      header['dateField'] = calendar.timegm(eutils.parsedate(msg.get("date")))
  except:
    #print 'date failed'
    return None
  # ========================
  header['threadid'] = getItem("X-GM-THRID", line1)
  header['UID'] = getItem("UID", line1)
  if line1.find("\\Sent") >= 0: header['isSent'] = True
  else: header['isSent'] = False
  return header


def getEmails(email, version, studyid):
  now = int(time.time())
  gmailStart = time.mktime(datetime.date(2004, 4, 1).timetuple())
  emails = []
  version = int(version)
  query = { 'email': email, 'version':version }
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  headers = db.emails.find_one(query)
  if headers is None: return None
  f = gzip.GzipFile(fileobj=cStringIO.StringIO(headers['contents']))
  headers = json.load(f)
  f.close()
  for header in headers:
    if header is None: continue
    try:
      if isinstance(header, (list, tuple)):
        parsedHeader = parseHeader(header[0], header[1])
      else: parsedHeader = header
    except:
      continue
      #print 'unhandled parsing exception!'
      #print header
      #print '>>> traceback <<<'
      #traceback.print_exc()
      #print '>>> end of traceback <<<'
      #exit(1)
    if parsedHeader is None or parsedHeader['dateField'] < gmailStart or parsedHeader['dateField'] > now: continue
      #print 'parsing failed or invalid date!!'
      #print header
    emails.append(parsedHeader)
  return emails

def getEmailsForUser(email, studyid):
  state = getState(email, studyid)
  nvers = state['version'] + 1
  allemails = []
  for version in xrange(nvers):
    emails = getEmails(email, version, studyid)
    if emails is None: return None
    allemails += emails
  return allemails

def getModifiedTimestamp(email, version, studyid):
  version = int(version)
  projection = {'timestamp': 1}
  query = {'email': email, 'version':version}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  emails = db.emails.find_one(query, projection)
  if emails is None: return None
  return emails['timestamp']

def getContentLength(email, version, studyid):
  version = int(version)
  projection = {'length': 1}
  query = {'email': email, 'version':version}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  emails = db.emails.find_one(query, projection)
  if emails is None: return None
  return emails['length']

def getEmailsContent(email, version, studyid):
  version = int(version)
  query = {'email': email, 'version':version}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  emails = db.emails.find_one(query)
  if emails is None: return None
  return emails['contents']


def deleteData(email, studyid):
  query = {'email' : email}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists': False}
  # delete task
  db.tasks.remove(query)
  # delete state
  db.states.remove(query)
  # delete emails
  db.emails.remove(query)

def submitFeedback(email, text, ip):
  db.feedback.insert({
    'email': email,
    'text': text,
    'ip' : ip,
    'timestamp': datetime.datetime.now(pytz.UTC)
  })

def waitlisting(email, ip):
  db.waitlist.insert({
    'email': email,
    'ip' : ip,
    'timestamp': datetime.datetime.now(pytz.UTC)
  })

def log(email, ip, module, msg, level=logging.INFO):
  timestamp = datetime.datetime.now(pytz.UTC)
  print '%s | %s | %s | %s | %s | %s' % (eutils.formatdate(time.time(), usegmt=True),
    logging.getLevelName(level), module, email, ip, msg)
  db.logs.insert({
    'email' : email,
    'ip' : ip,
    'module' : module,
    'msg' : msg,
    'level' : level,
    'timestamp': timestamp
  })

def popTask():
  task = db.tasks.find_and_modify(sort=[("timestamp", pymongo.ASCENDING)], remove=True)
  return task

def get(key):
  tmp = db.dict.find_one({'key': key})
  if tmp is not None: return tmp['value']
  return None

def put(key, value):
  doc = {'key': key, 'value': value}
  db.dict.update({'key': key}, doc, upsert=True)


def getTaskTimeAheadofQueue():
  tmp = db.tasks.find_one(sort=[("timestamp", pymongo.ASCENDING)])
  if tmp is not None: return tmp['timestamp']
  return None

def hasTask(email, studyid):
  query = {'email' : email}
  if studyid: query['studyid'] = studyid
  else: query['studyid'] = {'$exists':False}
  return db.tasks.find_one(query) is not None

def pushTask(email, studyid, authid=None):
  task = {'email': email, 'timestamp': datetime.datetime.now(pytz.UTC) , 'authid': authid}
  if studyid: task['studyid'] = studyid
  db.tasks.insert(task)


def pushTaskObject(task):
  db.tasks.insert(task)

def pushNotifyDone(email):
  db.notifications.update({'email': email}, {'$set': {'done':True}}, upsert=True)

def pushNotifyImap(email):
  db.notifications.update({'email': email}, {'$set': {'imap':True}}, upsert=True)

def popNotify():
  task = db.notifications.find_and_modify(remove=True)
  return task

def storeError(email, error):
  error['email'] = email
  error['timestamp'] = datetime.datetime.now(pytz.UTC)
  db.errors.update({'email':email}, error, upsert=True)

def removeAuthRequest(authid):
  return db.authrequests.remove({'_id': ObjectId(authid)})


def insertAuthRequest(username, email, password, emailType, studyid):
  authRequest = {'username': username, 'email':email, 'password': password, 'emailType': emailType, 'status': AuthStatus.PENDING}
  if studyid: authRequest['studyid'] = studyid
  return str(db.authrequests.insert(authRequest))

def getAuthRequest(authid):
  return db.authrequests.find_one({'_id': ObjectId(authid)})

