import logging
import logging.handlers
import smtplib
import string # for tls add this line
from email.utils import formatdate
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
import random

def sendEmailWithNetwork(email, filename, host):
  # me == my email address
  # you == recipient's email address
  idx = random.randint(1, 9)
  me = "em" + str(idx) + "@immersion.media.mit.edu"
  you = email

  # Create message container - the correct MIME type is multipart/alternative.
  msg = MIMEMultipart('alternative')
  msg['Subject'] = "Your Immersion snapshot"
  msg['From'] = "Immersion <" + me + ">"
  msg['To'] = you

  # Create the body of the message (a plain-text and an HTML version).
  text = "Here is the link to your Immersion snapshot:\n" + host + '/' + filename
  html = """\
    <html>
      <head></head>
      <body>
        <p>
          Thank you for visiting Immersion!<br/>
          Click <a href=\"""" + host + '/' + filename + """\">here</a> to view the snapshot of your network. 
        </p>
        <p>
          Cheers!<br/><a href=\"https://immersion.media.mit.edu\">Immersion</a>  |  <a href=\"http://macro.media.mit.edu\">Macro Connections</a>  |  <a href=\"http://media.mit.edu\">MIT Media Lab</a>
        </p>
      </body>
    </html>
  """

  # Record the MIME types of both parts - text/plain and text/html.
  part1 = MIMEText(text, 'plain')
  part2 = MIMEText(html, 'html')

  # Attach parts into message container.
  # According to RFC 2046, the last part of a multipart message, in this case
  # the HTML message, is best and preferred.
  msg.attach(part1)
  msg.attach(part2)

  # Send the message via local SMTP server.
  smtp = smtplib.SMTP('smtp.gmail.com', 587)
  # sendmail function takes 3 arguments: sender's address, recipient's address
  # and message to send - here it is sent as one string.
  smtp.ehlo() # for tls add this line
  smtp.starttls() # for tls add this line
  smtp.ehlo() # for tls add this line
  smtp.login(me, 'Webs0fpe0ple')
  smtp.sendmail(me, you, msg.as_string())
  smtp.quit()

class TlsSMTPHandler(logging.handlers.SMTPHandler):

  def emit(self, record):
    """
    Emit a record.

    Format the record and send it to the specified addressees.
    """
    try:
        port = self.mailport
        if not port: port = smtplib.SMTP_PORT
        smtp = smtplib.SMTP(self.mailhost, port)
        msg = self.format(record)
        msg = "From: %s\r\nTo: %s\r\nSubject: %s\r\nDate: %s\r\n\r\n%s" % (
                        self.fromaddr,
                        string.join(self.toaddrs, ","),
                        self.getSubject(record),
                        formatdate(), msg)
        if self.username:
            smtp.ehlo() # for tls add this line
            smtp.starttls() # for tls add this line
            smtp.ehlo() # for tls add this line
            smtp.login(self.username, self.password)
        smtp.sendmail(self.fromaddr, self.toaddrs, msg)
        smtp.quit()
    except (KeyboardInterrupt, SystemExit):
        raise
    except:
        self.handleError(record)
