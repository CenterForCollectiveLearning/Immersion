import numpy
import db
from db import db as db_direct
import time
import calendar

x = []
c = 0
a = 0
keys = None
nfailed = 0
for stat in db_direct['person_stats'].find():
    row = []
    local_keys = []
    failed = False
    for key in stat['stats']:
        if key in ['email', 'theirReplyTimeLength', 'myReplyTimeLength']: continue
        local_keys.append(key)
        value = stat['stats'][key]
        if type(value) == unicode or type(value) == str:
            try:
                value = calendar.timegm(time.strptime(value,'%b %d, %Y %I:%M:%S %p'))
            except:
                failed = True
                break

        row.append(value)
    if failed:
        nfailed += 1
        continue
    x.append(row)
    if keys is None:
        keys = local_keys

    if keys != local_keys: print 'ERROR!!!'
    if stat['stats']['myReplyTimeLength'] > 10 and stat['stats']['theirReplyTimeLength'] > 10:
      c+=1
      if stat['stats']['myReplyTime50'] > stat['stats']['theirReplyTime50']: a+=1

print 'no of failed', nfailed, '/', len(x)

m = numpy.corrcoef(x, rowvar=0)
print m.shape

seen = set()
for tmp in numpy.argsort(m, axis=None):#[:50]:
    i = tmp/m.shape[0]
    j = tmp%m.shape[0]
    tupl = (min(i,j), max(i,j))
    if tupl in seen: continue
    seen.add(tupl)
    print keys[i], keys[j], m[i,j]

print m[keys.index('myReplyTimeAvg'), keys.index('theirReplyTimeAvg')]
exit(1)

def eliminateOutliers(a, b):
    lowerA, upperA = numpy.percentile(a, 1), numpy.percentile(a, 99)
    lowerB, upperB = numpy.percentile(b, 1), numpy.percentile(b, 99)
    
    resA, resB = [], []
    for x, y in zip(a,b):
        if x < lowerA or x > upperA or y < lowerB or y > upperB: continue
        resA.append(x)
        resB.append(y)
    return resA, resB


f = open('MeVsYouReplyScatter.txt')
f.readline()
A = [[] for i in xrange(6)]
for line in f:
    ss = line.split('\t')
    for i in xrange(6):
        A[i].append(int(ss[i]))


a, b = A[0], A[1]
c = 0
for x,y in zip(a,b):
    if x > y: c+=1 # if I'm slower than the others
print (float(c)/len(a))*100, "% are slower than the others"
exit(1)
print len(a), len(b)
print scipy.stats.pearsonr(a,b)
a, b = eliminateOutliers(a, b)
print len(a), len(b)
print scipy.stats.pearsonr(a,b)
for x,y in zip(a,b):
    print "%d\t%d" % (x,y)

f.close()
