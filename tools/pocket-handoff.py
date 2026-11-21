"""Export the default mobile desk as editable SVG layers for import into Figma."""
from pathlib import Path
from xml.etree import ElementTree as E
root_dir = Path(__file__).resolve().parents[1]
art = root_dir/'chat-android/pocket/art'
svg = E.Element('svg', xmlns='http://www.w3.org/2000/svg', width='412', height='1250', viewBox='0 0 412 1250')

def elem(parent, tag, **attrs): return E.SubElement(parent,tag,{k.replace('_','-'):str(v) for k,v in attrs.items()})
def rect(g,x,y,w,h,fill,r=0,stroke='none'): elem(g,'rect',x=x,y=y,width=w,height=h,rx=r,fill=fill,stroke=stroke)
def text(g,x,y,label,size=14,weight=500,color='#29252e'): elem(g,'text',x=x,y=y,font_family='Manrope, sans-serif',font_size=size,font_weight=weight,fill=color).text=label
def artwork(g,name,x,y,scale):
    group=elem(g,'g',id=name,transform=f'translate({x} {y}) scale({scale})')
    for node in E.parse(art/(name+'.svg')).getroot():
        for n in node.iter(): n.tag=n.tag.split('}')[-1]
        group.append(node)
def group(name):return elem(svg,'g',id=name)
rect(svg,0,0,412,1250,'#fbfaf7'); g=group('Brand');rect(g,0,0,412,60,'#fffefa');rect(g,18,12,36,36,'#e3d8fa',12);artwork(g,'mark',21,20,1.18);text(g,62,39,'Mimi',24,800);text(g,129,37,'/   Pocket Desk',14,650);rect(g,352,10,40,40,'#f7eed8',20);text(g,367,35,'P',15,700)
g=group('Workspace');text(g,18,91,'✦   PERSONAL WORKSPACE',10,700,'#76678a');text(g,18,134,'My pocket desk.',37,800);text(g,18,158,'A little space for Mimi, Pavit’s own AI bot.',13);rect(g,322,104,72,28,'#f3f0ea',14);text(g,332,122,'●  Offline',11,700)
g=group('Mimis-world');rect(g,18,178,376,177,'#eae0f8',22,'#cdbbe7');rect(g,34,197,110,22,'#f9f3ff',11);text(g,44,212,'✦  MIMI’S WORLD',10,700,'#6f5189');text(g,34,250,'Just hanging out.',22,800);text(g,34,273,'You’re available, so Mimi is',12);text(g,34,290,'taking a little break.',12);rect(g,34,307,116,26,'#fffafc',13);text(g,44,324,'●   Replies paused',11,700);artwork(g,'island-off',219,210,1.1)
g=group('Availability');text(g,18,388,'01  /  AVAILABILITY',10,700,'#867098');text(g,18,419,'Set your vibe',27,800);text(g,18,443,'Pick a mode. Mimi will follow your lead.',14)
for i,(mode,name,sub,fill) in enumerate([('off','Available','Pause replies','#edf5e9'),('busy','Busy','Focus time','#fcf0de'),('sleeping','Sleeping','Rest easy','#f1ebfa'),('driving','Driving','On the road','#e5f2f4')]):
    x=18+i%2*193;y=459+i//2*117;item=elem(g,'g',id=name);rect(item,x,y,182,106,fill,14,'#73628e' if i==0 else '#ded9d5');artwork(item,mode,x+12,y+10,2.3);text(item,x+14,y+71,name,17,750);text(item,x+14,y+90,sub,12);rect(item,x+151,y+12,15,15,'#73628e' if i==0 else '#fff',8,'#bbb1bf');
    if not i:text(item,x+154,y+24,'✓',11,700,'white')
text(g,18,709,'Saved on this phone, even when offline.',11);g=group('Toolkit');text(g,18,749,'02  /  QUICK TOOLS',10,700,'#867098');text(g,18,780,'Mimi’s toolkit',27,800)
for i,(title,sub,fill) in enumerate([('Reply studio','Preview your greeting & language','#f1ebfa'),('Call Shield','Manage declines & priority callers','#edf5e9'),('Follow-up inbox','Review callbacks & urgent notes','#fcf0de'),('Account & AI','Manage your number & provider','#e5f2f4')]):
    y=799+i*75;rect(g,18,y,376,65,'#fffdfb',14,'#ded9d5');rect(g,32,y+12,40,40,fill,12);text(g,44,y+37,['✎','◇','▤','⚙'][i],18);text(g,84,y+27,title,16,750);text(g,84,y+46,sub,12);text(g,366,y+37,'›',22)
g=group('Connection');text(g,18,1127,'03  /  CONNECTION',10,700,'#867098');text(g,18,1159,'WhatsApp Business',27,800);rect(g,18,1173,376,60,'#fffdfb',14,'#ded9d5');text(g,30,1207,'Engine offline',14,700);rect(g,216,1184,166,39,'#60507a',12);text(g,233,1209,'Connect WhatsApp',14,700,'white')
(root_dir/'design').mkdir(exist_ok=True);E.ElementTree(svg).write(root_dir/'design/Mimi-Pocket-Desk.svg',encoding='utf-8')
