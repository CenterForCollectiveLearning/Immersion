import os,sys
parentdir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0,parentdir) 
import gzip
import json
import email as em
from email.header import decode_header
import time
import re
import db
import random
import csv

#### global variables ####
addrIdx = 1
email2anon = {}
name2anon = {}
orig_name = 'Daniel Smilkov'
orig_email = 'dsmilkov@gmail.com'
version = 2
#########################


# load first names
firstnames = []
f = open('firstnames.csv','rU')
reader = csv.reader(f)
for row in reader:
  firstnames.append(row[0].strip())
f.close()
print len(firstnames)

# load last names
lastnames = []
f = open('lastnames.csv', 'rU')
reader = csv.reader(f)
for row in reader:
  lastnames.append(row[0].strip())
f.close()
print len(lastnames)


## read demodata
data = []
for ver in range(version+1):
  data += db.getEmails(orig_email, ver)
print len(data), 'emails loaded from db'

def getAddresses(field):
  field = field.replace('\r\n',' ').replace('\n', ' ')
  addrs = em.utils.getaddresses([field])
  result = []
  for name, email in addrs:
    ss = decode_header(name)
    decod_name = ""
    for s, encoding in ss:
      if encoding is None: encoding = 'ascii'
      try: decod_name += s.decode(encoding)
      except: pass
    result.append((decod_name, email.lower()))
  return result

'''   Returns a normalized version of the name. '''
def normalize_name(name, email):
  name = name.replace("'",'').replace('"','')
  name = re.sub(r"\(.*\)", "", name).strip().lower()
  email = email.lower()
  if name == "" or name == email: return email.strip().lower()
  if ',' in name:
    ss = name.split(',')
    first = ss[-1].strip().title()
    last = ss[0].strip().title()
  else:
    ss = name.split()
    if len(ss) == 1: return ss[0].strip().title()
    first = ss[0].strip().title()
    last = ss[-1].strip().title()
  return first + " " + last

def annoNameAndEmail(name, email):
  global addrIdx
  global email2anon
  global name2anon
  global orig_name
  global orig_email
  anno_name, anno_email = None, None
  

  # anonymize name
  if name == orig_name:
    anno_name = 'Demo User'
  elif name in email2anon:
    anno_name = email2anon[name]
  else:
    if name not in name2anon:
      # generate a new unused annonymous name
      name2anon[name] = firstnames[random.randint(0, len(firstnames)-1)] + " " + lastnames[random.randint(0, len(lastnames)-1)]
    anno_name = name2anon[name]

  # anonymize email
  if email == orig_email:
    anno_email = 'demo@demo.com'
  else:
    if email not in email2anon:
      email2anon[email] = anno_name.lower().replace(" ", '.') + "@fict.mit.edu";
      addrIdx+=1
    anno_email = email2anon[email]
  # consistency check
  if name == email: anno_name = anno_email
  return anno_name, anno_email

def serializeContact(contact):
  name, addr = contact
  return '"' + name + '" <' + addr + '>'

def anonymize(data):
  # first parse data into emails
  emails = []
  for i in xrange(len(data)):
    try:
      mail = {}
      header = data[i][1]
      msg = em.message_from_string(header)
      tmp = getAddresses(msg.get('From',''))[0]
      if len(tmp) == 0: continue
      mail['fromField'] = (normalize_name(tmp[0], tmp[1]), tmp[1].strip().lower())
      #########
      mail['toField'] = getAddresses(msg.get('To',''))
      for k in xrange(len(mail['toField'])):
        mail['toField'][k] = (normalize_name(mail['toField'][k][0], mail['toField'][k][1]), mail['toField'][k][1].strip().lower())
      ##########
      mail['ccField'] = getAddresses(msg.get('CC',''))
      for k in xrange(len(mail['ccField'])):
        mail['ccField'][k] = (normalize_name(mail['ccField'][k][0], mail['ccField'][k][1]), mail['ccField'][k][1].strip().lower())
      ########
      date = msg.get('Date','')
      if date is None or date == '': continue
      mail['dateField'] = date
      if '\\\\Sent' in data[i][0]:
        #if mail['fromField'][0] != 'Daniel Smilkov': print mail['fromField']
        mail['isSent'] = True
      emails.append(mail)
    except:
      pass
  
  # anonymize emails
  # map each email address to a unique anonymized address
  # and each name to a unique name
  for email in emails:
    # from field
    name, addr = email['fromField']
    name, addr = annoNameAndEmail(name, addr)
    email['fromField'] = (name, addr)

    # to field
    for k in xrange(len(email['toField'])):
      name, addr = email['toField'][k]
      name, addr = annoNameAndEmail(name, addr)
      email['toField'][k] = (name, addr)

    # cc field
    for k in xrange(len(email['ccField'])):
      name, addr = email['ccField'][k]
      name, addr = annoNameAndEmail(name, addr)
      email['ccField'][k] = (name, addr)
  
  #serialize emails in raw format
  annodata = []
  for email in emails:
    a = ''
    if 'isSent' in email: a = '\\\\Sent'
    # from
    b = 'From: ' + serializeContact(email['fromField']) + '\r\n'
    # to
    b += 'To: ' + ", ".join([serializeContact(contact) for contact in email['toField']]) + '\r\n'
    # cc
    b += 'Cc: ' + ", ".join([serializeContact(contact) for contact in email['ccField']]) + '\r\n'
    b += 'Date: ' + email['dateField'] + '\r\n\r\n'
    annodata.append([a,b])
  return annodata  

annodata = anonymize(data)
tmp = range(0, len(annodata), 10000)
for i in xrange(len(tmp)):
  if i+1 < len(tmp): partial = annodata[tmp[i]:tmp[i+1]]
  else: partial = annodata[tmp[i]:]
  db.storeEmails("demo@demo.com", partial, i)
