"""Build the local, editable pixel-art assets. No remote images or fonts at runtime."""
from pathlib import Path
out=Path(__file__).resolve().parents[1]/'chat-android/pocket/art'
out.mkdir(exist_ok=True)
def rect(x,y,w,h,c):return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{c}"/>'
def poly(points,c):return f'<polygon points="{points}" fill="{c}"/>'
def pixels(rows,palette,x=0,y=0,scale=1):
    return ''.join(rect(x+j*scale,y+i*scale,scale,scale,palette[v]) for i,row in enumerate(rows) for j,v in enumerate(row) if v in palette)
def svg(w,h,s):return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" shape-rendering="crispEdges">{s}</svg>'
def save(name,w,h,s): (out/(name+'.svg')).write_text(svg(w,h,s))
cloud=pixels(['....aaaaaa......','..aabbbbbbaaa...','.abbbbbbbbbbbba.','abbbbbbbbbbbbbba','aaaaaaaaaaaaaaaa'],{'a':'#cfc1f1','b':'#fffdff'})
heart=['.xx.xx.','xxxxxxx','xxxxxxx','.xxxxx.','..xxx..','...x...']
spark=lambda x,y,c:rect(x+2,y,2,6,c)+rect(x,y+2,6,2,c)
# Compact editorial icons, using the same pixel scale as the world illustration.
icons={'off':(['.......yyy......','.....yywwyy.....','...yywwwwwwyy...','...ywwwyywwwy...','.yywwyyggyywwyy.','.ywwyyggggyywwy.','.yywwyyggyywwyy.','...ywwwyywwwy...','...yywwwwwwyy...','.....yywwyy.....','.......gg.......','...ggg.gg.......','....ggggg.......','.......gg.......'],{'y':'#e9ad3d','w':'#ffdd74','g':'#3c8057'}),'busy':(['.....ss..ss.....','....s...ss......','................','..ppppppppp.....','..pcccccccpppp..','..pcccccccpc.p..','..pccwccccpc.p..','..pccwccccpppp..','..pcccccccp.....','...pcccccp......','....ppppp.......','.ppppppppppppp..','..lllllllllll...'],{'p':'#945e46','s':'#af8a72','c':'#f3a780','w':'#ffd8ba','l':'#dbad8a'}),'sleeping':(['.......dddd.....','.....ddyyyd.....','....dyyyydd.....','...dyyyyyd......','..dyyyyydd...s..','..dyyyyyd...sss.','..dyyyyydd...s..','..dyyyyyydd.....','...dyyyyyyydddd.','...dyyyyyyyyyyd.','....ddyyyyyydd..','......dddddd....'],{'d':'#ac8542','y':'#ffde78','s':'#9e76c1'}),'driving':(['................','.....dddddd.....','....dggddbbd....','...dgggddbbbd...','..dddddddddddd..','.dggggggggggggd.','.dwggggggggggwd.','.dggddggggddggd.','..ddppddddppdd..','....pp....pp....'],{'d':'#406e68','g':'#82ccaa','b':'#d4edf0','w':'#ffe3a1','p':'#474258'})}
for name,(rows,pal) in icons.items():save(name,16,16,pixels(rows,pal))
# Pocket rabbit: differently posed pixel ears, outline, fur highlights, scarf and small feet.
bunny=['.....kkk........kkk.....','....kwwwk......kwwwk....','....kwpwk......kwpwk....','....kwppwk....kwppwk....','....kwppwk....kwppwk....','....ksppwk....kwppwk....','.....kswwkkkkkkwwwk.....','.....kwwwwwwwwwwwwk.....','....kwwwwwwwwwwwwwwk....','...kwwwwwwwwwwwwwwwwk...','..kwwwwwwwwwwwwwwwwwwk..','..kwwwwwwwwwwwwwwwwwwk..','.kswwwwkwwwwwwkwwwwwwsk.','.kswwwwkwwwwwwkwwwwwwsk.','.kswwppwwwkkwwwppwwwwsk.','..kswppwwkwwkwwppwwwsk..','...ksswwwwwwwwwwwwssk...','....ksswwwwwwwwwwssk....','....kggggggggggggggk....','...kwsgggggggggggswsk...','..kwsswwwwwgggwwwwswsk..','..ksskwwwwwgggwwwwkssk..','...kkksswwwwwwwwsskkk...','.....ksswwwwwwwwssk.....','....kwwwkkkkkkkkwwwk....','....kkkkk......kkkkk....']
pal={'k':'#443247','w':'#fff9ed','s':'#e1d5df','p':'#f3a7ba','g':'#78b898'}
save('mimi',26,27,pixels(bunny,pal));save('mark',26,20,pixels(bunny[:18],pal));
for mode in icons:
 s='';s+=f'<g transform="translate(10 15) scale(1.3)">{cloud}</g><g transform="translate(116 5) scale(1.15)">{cloud}</g>'
 for x,y in [(35,33),(114,33),(144,49),(20,57),(132,72),(48,13)]:s+=spark(x,y,'#fffbee' if x%2 else '#bda7dd')
 s+=poly('10,77 23,77 23,73 43,73 43,71 109,71 109,75 130,75 130,79 139,79 139,87 128,87 128,91 101,91 101,94 53,94 53,91 28,91 28,87 10,87','#cdb9df')
 s+=poly('21,60 32,60 32,55 48,55 48,51 95,51 95,54 112,54 112,58 127,58 127,62 134,62 134,73 126,73 126,78 115,78 115,82 97,82 97,86 62,86 62,83 44,83 44,79 31,79 31,75 21,75','#806975')
 s+=poly('24,64 32,64 32,68 48,68 48,71 64,71 64,75 99,75 99,72 116,72 116,68 131,66 131,71 123,71 123,77 111,77 111,80 97,80 97,83 64,83 64,81 46,81 46,77 33,77 33,73 24,73','#c7a091')
 s+=poly('21,59 32,59 32,55 47,55 47,51 96,51 96,54 112,54 112,57 125,57 125,61 134,61 134,67 127,67 127,70 116,70 116,73 99,73 99,77 65,77 65,75 48,75 48,72 32,72 32,68 21,68','#5b9178')
 s+=poly('23,60 33,60 33,56 48,56 48,52 95,52 95,55 111,55 111,58 124,58 124,62 132,62 132,65 124,65 124,68 115,68 115,71 98,71 98,74 65,74 65,72 49,72 49,69 33,69 33,66 23,66','#a2d69c')
 for x,y in [(30,61),(50,65),(108,64),(102,70),(49,58),(74,71),(111,59),(62,56),(35,65)]:s+=rect(x,y,4,1,'#81b98a')+rect(x+4,y-1,2,1,'#81b98a')
 # Soil details, grass tufts, and tiny flowers
 for x,y in [(38,73),(51,77),(67,80),(89,78),(114,74)]:s+=rect(x,y,4,2,'#a4807b')
 for x,y in [(30,60),(113,57),(120,65),(52,70),(47,56)]:s+=rect(x,y-3,1,4,'#648f69')+rect(x-1,y-2,1,2,'#80ad70')+rect(x+1,y-4,1,3,'#689b71')
 for x,y,c in [(39,58,'#fff2c5'),(43,61,'#ffe6ef'),(106,58,'#fff0ad'),(114,65,'#f4b4ca'),(53,66,'#fff8dd')]:s+=rect(x,y,1,4,'#5e9368')+rect(x-2,y-2,5,2,c)+rect(x-1,y-3,3,4,c)+rect(x,y-2,1,2,'#e5b660')
 s+=rect(62,65,31,2,'#85b98a'); work=bunny[:]; colors=dict(pal)
 if mode=='sleeping':work[12]=work[13]='.kswwwwwwwwwwwwwwwwwwsk.';work[13]='.kswwwkkkwwwwkkkwwwwwsk.';colors['g']='#ad97cf'
 if mode=='busy':work[11]='..kwwwkkkkwwkkkkwwwwwk..'; colors['g']='#e7ac79'
 if mode=='driving':work[12]='.kswwwkkkkkkkkkkwwwwwsk.';work[13]='.kswwwkkkwwwwkkkwwwwwsk.';colors['g']='#72b9ca'
 s+=pixels(work,colors,59,17,1.85)
 # Pixel speech bubble with heart or moon/mug/car badge
 s+=poly('98,14 101,14 101,12 118,12 118,14 121,14 121,26 118,26 118,29 105,29 101,33 101,29 98,29','#796783')+poly('100,15 102,15 102,14 117,14 117,15 119,15 119,25 116,25 116,27 104,27 103,29 103,27 100,27','#fffbfb')
 s+=pixels(heart,{'x':'#ef5da8'},104,17,1.5) if mode=='off' else pixels(*icons[mode],103,15,.83)
 save('island-'+mode,154,98,s)
