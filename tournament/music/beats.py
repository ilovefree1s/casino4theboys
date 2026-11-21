import numpy as np, subprocess
from scipy.signal import butter, sosfilt
from scipy.io import wavfile
SR=44100; rng=np.random.default_rng(5)
def lp(x,f,o=2): return sosfilt(butter(o,f,btype='low',fs=SR,output='sos'),x)
def hp(x,f,o=2): return sosfilt(butter(o,f,btype='high',fs=SR,output='sos'),x)
def bp(x,a,b): return sosfilt(butter(2,[a,b],btype='band',fs=SR,output='sos'),x)
def hz(m): return 440*2**((m-69)/12)
def T(n): return np.arange(int(SR*n))/SR
def kick(f0=130,f1=42,d=.28,punch=.03):
    t=T(d+.1); f=f1+(f0-f1)*np.exp(-t/punch); ph=2*np.pi*np.cumsum(f)/SR
    return np.tanh(1.6*np.sin(ph)*np.exp(-t/d*2.2))
def snare(d=.17):
    t=T(d+.08); n=bp(rng.standard_normal(len(t)),900,7500)*np.exp(-t/(d*.45)); n/=np.abs(n).max()
    return .75*n+.5*np.sin(2*np.pi*185*t)*np.exp(-t/.05)
def clap():
    t=T(.25); n=bp(rng.standard_normal(len(t)),1100,5200); e=np.zeros(len(t))
    for d0 in (0,.011,.023): e+=np.where(t>=d0,np.exp(-(t-d0)/.012),0)
    e+=np.where(t>=.03,np.exp(-(t-.03)/.07),0)*.6; y=n*e; return y/np.abs(y).max()
def hat(d=.035,open_=False):
    t=T(.3 if open_ else .08); n=hp(rng.standard_normal(len(t)),7000); y=n*np.exp(-t/(.12 if open_ else d)); return y/np.abs(y).max()
def rim():
    t=T(.06); return (np.sin(2*np.pi*1700*t)+.6*np.sin(2*np.pi*520*t))*np.exp(-t/.012)
def sub(m,n,glide=None,drive=1.8):
    t=T(n); f=np.full(len(t),hz(m))
    if glide is not None: f=hz(glide)+(hz(m)-hz(glide))*np.minimum(1,t/.09)
    ph=2*np.pi*np.cumsum(f)/SR; e=np.minimum(1,t/.006)*np.exp(-t/(n*1.3)); e[-int(SR*.02):]*=np.linspace(1,0,int(SR*.02))
    return np.tanh(drive*np.sin(ph))*e
def keys(notes,n,bright=1400,trem=0):
    t=T(n); y=np.zeros(len(t))
    for m in notes:
        f=hz(m); det=rng.uniform(-.0015,.0015)
        y+=np.sin(2*np.pi*f*(1+det)*t)+.35*np.sin(2*np.pi*2*f*t)*np.exp(-t/.4)+.12*np.sin(2*np.pi*3*f*t)*np.exp(-t/.2)
    e=np.minimum(1,t/.008)*(.35+.65*np.exp(-t/.5))*np.exp(-t/(n*1.1)); e[-int(SR*.03):]*=np.linspace(1,0,int(SR*.03))
    y=lp(y*e,bright)
    if trem: y*=1+.12*np.sin(2*np.pi*trem*t)
    return y/len(notes)
def pluck(m,n=.5,bright=3000):
    t=T(n); f=hz(m); y=(np.sin(2*np.pi*f*t)+.5*np.sin(2*np.pi*2.01*f*t)*np.exp(-t/.08)+.25*np.sin(2*np.pi*4.02*f*t)*np.exp(-t/.04))*np.exp(-t/(n*.35))
    return lp(y,bright)
def pad(notes,n):
    t=T(n); y=np.zeros(len(t))
    for m in notes:
        for d in (-.004,0,.004): 
            ph=2*np.pi*hz(m)*(1+d)*t; y+=np.sin(ph)+.3*np.sin(2*ph)
    e=np.minimum(1,t/.4)*np.minimum(1,(n-t)/.5); return lp(y*e,900)/(3*len(notes))
class Mix:
    def __init__(s,dur): s.y=np.zeros(int(SR*dur)); s.dur=dur
    def add(s,t,x,g=1.0):
        i=int(t*SR)%len(s.y); n=len(x)
        if i+n<=len(s.y): s.y[i:i+n]+=x*g
        else:                          # wrap the tail round to the top so the loop is seamless
            k=len(s.y)-i; s.y[i:]+=x[:k]*g; s.y[:n-k]+=x[k:]*g
def reverb(y,amt=.18,sec=1.1):
    n=int(SR*sec); ir=rng.standard_normal(n)*np.exp(-np.arange(n)/SR/(sec/5)); ir=lp(ir,5000)
    Y=np.fft.rfft(np.concatenate([y,y])); wet=np.fft.irfft(Y*np.fft.rfft(ir,len(y)*2))[len(y):]   # circular: tail wraps
    return y+amt*wet/np.abs(wet).max()*np.abs(y).max()
def save(name,y):
    y=hp(np.concatenate([y,y,y]),28)[len(y):2*len(y)]
    y=np.tanh(1.25*y/np.abs(y).max())*.9
    full=np.concatenate([y,y]); f=int(SR*.02); full[:f]*=np.linspace(0,1,f); full[-int(SR*.6):]*=np.linspace(1,0,int(SR*.6))
    wavfile.write(name+'_preview.wav',SR,(full*32767).astype(np.int16))
    wavfile.write(name+'_loop.wav',SR,(y*32767).astype(np.int16))
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',name+'_preview.wav','-codec:a','libmp3lame','-q:a','3',name+'.mp3'],check=True)
    print(name,"loop %.2fs"%(len(y)/SR))

# ---------- 1) BOOM BAP, 90 bpm: dusty drums, a fat bass, electric piano chops — the graffiti one ----------
bpm=90; b=60/bpm; M=Mix(b*16)
prog=[([57,60,64,67],45),([53,57,60,64],41),([50,53,57,60],38),([52,56,59,62],40)]     # Am7 Fmaj7 Dm7 E7
K,S,H,Ho=kick(),snare(),hat(),hat(open_=True)
for bar in range(4):
    t0=bar*4*b; ch,root=prog[bar]
    for kb in ([0,1.5,2.5] if bar%2==0 else [0,1.75,2.5,3.5]): M.add(t0+kb*b,K,1.0)
    for sb in (1,3): M.add(t0+sb*b,S,.8)
    for e in range(8):
        sw=.08*b if e%2 else 0; M.add(t0+e*b/2+sw,H,.22 if e%2 else .3)
    if bar%2: M.add(t0+3.5*b+.08*b,Ho,.2)
    for (bt,ln,nt) in [(0,.9,root),(1.5,.45,root),(2.5,.9,root+ (7 if bar%2 else 0)),(3.5,.4,root+3 if bar!=3 else root-1)]:
        M.add(t0+bt*b,lp(sub(nt,ln*b),500),.75)
    M.add(t0,keys(ch,b*1.4,1500,5),.55); M.add(t0+2.5*b,keys(ch,b*.5,1900),.42); M.add(t0+3.25*b,keys([n+12 for n in ch[1:]],b*.6,2200),.3)
vinyl=lp(hp(rng.standard_normal(len(M.y)),2000),6000)*.006
save('1_boom_bap', reverb(M.y,.12,.8)+vinyl)

# ---------- 2) TRAP, 140 bpm half-time: 808 slides, rolling hats, clap, a dark bell line ----------
bpm=140; b=60/bpm; M=Mix(b*32)
roots=[38,38,34,36]; scale=[62,65,69,70,74,77]                # D minor
C=clap(); 
for bar in range(8):
    t0=bar*4*b; r=roots[(bar//2)%4]
    M.add(t0+2*b,C,.75)
    pat=[(0,2.6,None),(3,.9,None)] if bar%2==0 else [(0,1.4,None),(1.5,.9,None),(2.75,1.2,r+12)]
    for bt,ln,gl in pat:
        M.add(t0+bt*b,K,.9); M.add(t0+bt*b,sub(r,ln*b,glide=gl,drive=2.6),.8)
    for e in range(8):
        M.add(t0+e*b/2,H,.24)
    if bar%2: 
        for r_ in range(6): M.add(t0+3*b+r_*b/6,H,.14+.03*r_)
    if bar%4==3: M.add(t0+3.5*b,Ho,.2)
    mel=[(0,3),(.75,2),(1.5,4),(2.5,2),(3.25,1)] if bar%2==0 else [(0,0),(1,2),(1.75,3),(3,5)]
    for bt,i in mel: M.add(t0+bt*b,pluck(scale[i]+12,.9,3500),.2)
    if bar%2==0: M.add(t0,pad([r+24,r+27,r+31],b*8),.5)
save('2_trap', reverb(M.y,.2,1.4))

# ---------- 3) LO-FI, 78 bpm: soft kick, rim, warm wobbly chords, sleepy ----------
bpm=78; b=60/bpm; M=Mix(b*16)
prog=[([60,64,67,71,74],36),([57,60,64,67,71],33),([53,57,60,64,67],41),([55,59,62,65,69],43)]   # Cmaj9 Am9 Fmaj9 G13
Ks=lp(kick(100,45,.22),900); R=rim()
for bar in range(4):
    t0=bar*4*b; ch,root=prog[bar]
    for kb in (0,2,2.75): M.add(t0+kb*b,Ks,.9)
    for sb in (1,3): M.add(t0+sb*b,R,.5); M.add(t0+sb*b,snare(.1),.22)
    for e in range(8): M.add(t0+e*b/2+(.1*b if e%2 else 0),H,.12)
    M.add(t0,lp(sub(root,b*1.9,drive=1.2),300),.8); M.add(t0+2.75*b,lp(sub(root,b*1.1,drive=1.2),300),.6)
    M.add(t0,keys(ch,b*3.6,1100,4),.8); M.add(t0+2.5*b,keys(ch[2:],b*1.2,1300),.35)
    for bt,n in [(1.5,ch[-1]+12),(2.25,ch[-2]+12),(3.5,ch[-1]+10)]: M.add(t0+bt*b,pluck(n,.8,2200),.16)
y=reverb(M.y,.22,1.5); t=np.arange(len(y))/SR
y=y*(1+.03*np.sin(2*np.pi*.6*t)); y=lp(y,6500)+lp(hp(rng.standard_normal(len(y)),1500),5000)*.008
save('3_lofi', y)
