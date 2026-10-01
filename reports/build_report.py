"""Build the project report using only Python's standard library.

Run --prepare, then render_diagrams.ps1, then run without arguments.
PDF diagrams remain vectors; DOCX uses high-resolution raster counterparts.
"""
from pathlib import Path
import json, math, re, sys, zipfile
from html import escape
from xml.sax.saxutils import escape as xe

ROOT = Path(__file__).resolve().parent
D = ROOT / 'diagrams'
D.mkdir(exist_ok=True)
INK = '#17324d'
BLUE = '#235c85'
PALE = '#edf4f9'

class Drawing:
    def __init__(self, name, width, height):
        self.name, self.width, self.height, self.shapes = name, width, height, []
    def add(self, kind, **kw):
        self.shapes.append(dict(kind=kind, **kw))
    def text(self, x, y, text, size=18, bold=False, center=False):
        self.add('text', x=x, y=y, text=text, size=size, bold=bold, center=center)
    def line(self, x1,y1,x2,y2, arrow=False, both=False):
        self.add('line', x1=x1,y1=y1,x2=x2,y2=y2)
        if arrow: self.arrow(x1,y1,x2,y2)
        if both: self.arrow(x2,y2,x1,y1)
    def arrow(self,x1,y1,x2,y2, hollow=False):
        a=math.atan2(y2-y1,x2-x1)
        pts=[[x2,y2],[x2-12*math.cos(a)+5*math.sin(a),y2-12*math.sin(a)-5*math.cos(a)],
             [x2-12*math.cos(a)-5*math.sin(a),y2-12*math.sin(a)+5*math.cos(a)]]
        self.add('polygon', points=pts, fill='#ffffff' if hollow else INK)
    def box(self,x,y,w,h,label='',kind='rect',size=18):
        self.add(kind,x=x,y=y,w=w,h=h,fill=PALE)
        for i,t in enumerate(label.split('\n')):
            self.text(x+w/2,y+h/2-(len(label.split('\n'))-1)*11+i*22-10,t,size,False,True)
    def actor(self,x,y,label):
        self.add('ellipse',x=x-12,y=y,w=24,h=24,fill='#ffffff')
        self.line(x,y+24,x,y+61)
        self.line(x-24,y+37,x+24,y+37)
        self.line(x,y+61,x-22,y+85)
        self.line(x,y+61,x+22,y+85)
        self.text(x,y+91,label,17,True,True)
    def entity(self,x,y,w,title,fields):
        h=36+len(fields)*23
        self.add('rect',x=x,y=y,w=w,h=h,fill='#ffffff')
        self.add('rect',x=x,y=y,w=w,h=31,fill=PALE)
        self.text(x+10,y+6,title,17,True)
        for i,t in enumerate(fields): self.text(x+10,y+38+i*23,t,16)
        return h
    def store(self,x,y,w,label):
        self.line(x,y,x+w,y); self.line(x,y+36,x+w,y+36)
        self.text(x+w/2,y+7,label,17,True,True)
    def save(self):
        data=dict(width=self.width,height=self.height,shapes=self.shapes)
        (D/f'{self.name}.json').write_text(json.dumps(data),encoding='utf-8')
        body=[]
        for s in self.shapes:
            k=s['kind']
            if k=='text':
                body.append(f'<text x="{s["x"]}" y="{s["y"]+s["size"]*.82}" font-size="{s["size"]}" font-weight="{"bold" if s["bold"] else "normal"}" text-anchor="{"middle" if s["center"] else "start"}">{escape(s["text"])}</text>')
            elif k=='line': body.append(f'<line x1="{s["x1"]}" y1="{s["y1"]}" x2="{s["x2"]}" y2="{s["y2"]}"/>')
            elif k=='polygon': body.append(f'<polygon points="{" ".join(str(x)+","+str(y) for x,y in s["points"])}" fill="{s["fill"]}"/>')
            elif k=='ellipse': body.append(f'<ellipse cx="{s["x"]+s["w"]/2}" cy="{s["y"]+s["h"]/2}" rx="{s["w"]/2}" ry="{s["h"]/2}" fill="{s["fill"]}"/>')
            else: body.append(f'<rect x="{s["x"]}" y="{s["y"]}" width="{s["w"]}" height="{s["h"]}" fill="{s["fill"]}"/>')
        svg=f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.width}" height="{self.height}" viewBox="0 0 {self.width} {self.height}"><rect width="100%" height="100%" fill="white"/><g stroke="{INK}" stroke-width="1.5" font-family="Arial" fill="{INK}">'+''.join(body)+'</g></svg>'
        # Text should not inherit an outline stroke.
        svg=svg.replace('<text ','<text stroke="none" ')
        (D/f'{self.name}.svg').write_text(svg,encoding='utf-8')

def diagrams():
    d=Drawing('use-case',960,552)
    d.add('rect',x=265,y=8,w=485,h=537,fill='#ffffff')
    d.text(507,16,'HRGenius - Core HR system',20,True,True)
    d.actor(108,22,'Authenticated user')
    d.actor(108,213,'HR administrator')
    d.actor(108,371,'Manager')
    d.actor(865,212,'Employee')
    d.box(794,478,145,62,'Mail service',size=17)
    # Generalization: specialized actors point to authenticated user.
    for pts in [[(108,213),(24,185),(108,130)],[(108,371),(8,343),(8,155),(108,130)],[(865,212),(947,182),(947,1),(108,1),(108,22)]]:
        for a,b in zip(pts,pts[1:]): d.line(*a,*b)
        d.arrow(*pts[-2],*pts[-1],hollow=True)
    cases=[(300,50,410,47,'Sign in / maintain session'),(300,110,410,47,'View directory and organization chart'),
           (300,173,410,47,'Manage employees / organization'),(300,235,410,47,'Manage assets / import and export'),
           (300,297,410,47,'Review documents and audit records'),(300,359,410,47,'View permitted team / own profile'),
           (300,423,410,47,'Maintain own contacts / documents'),
           (300,485,410,47,'Send document expiry digest')]
    for x,y,w,h,label in cases: d.box(x,y,w,h,label,'ellipse',17)
    d.line(133,59,300,74); d.line(133,77,300,132)
    for y in (196,259,320): d.line(132,252,300,y)
    d.line(132,410,300,382)
    d.line(841,251,710,382); d.line(841,269,710,446)
    d.line(710,508,794,508)
    d.line(132,273,227,508);d.line(227,508,300,508)
    d.save()

    d=Drawing('entity-relationship',960,594)
    # Connectors are drawn before boxes to keep endpoints clean.
    for args in [(247,65,280,65),(280,65,280,200),(280,200,350,200),(455,97,455,185),(665,232,720,232),(665,282,720,377),
                 (350,246,242,246),(350,302,242,400),(460,354,460,443),(710,493,583,493)]: d.line(*args)
    d.text(252,42,'0..1',16); d.text(302,177,'0..*',16)
    d.text(464,130,'0..1 manager',16); d.text(464,164,'0..* reports',16)
    d.text(674,210,'1',16); d.text(674,235,'0..1',16)
    d.text(670,291,'1',16); d.text(680,342,'0..*',16)
    d.text(301,221,'1',16); d.text(253,250,'0..*',16)
    d.text(294,307,'1',16); d.text(253,367,'0..*',16)
    d.text(468,367,'1',16); d.text(468,415,'0..*',16)
    d.text(677,467,'1',16); d.text(600,468,'0..*',16)
    d.entity(15,15,232,'DEPARTMENTS',['PK id','UQ code','name'])
    d.entity(350,15,238,'EMPLOYEES (manager)',['PK id','name'])
    d.entity(350,185,315,'EMPLOYEES',['PK id; UQ employee_code','UQ work_email','FK department_id (nullable)','FK manager_id (nullable)','first_name; last_name','status'])
    d.entity(720,174,225,'EMPLOYEE_STATUTORY',['PK, FK employee_id','pan_enc; aadhaar_enc','bank_account_enc'])
    d.entity(15,197,227,'EMPLOYEE_DOCUMENTS',['PK id; FK employee_id','storage_key; title','expiry_date; verified'])
    d.entity(15,357,227,'EMERGENCY_CONTACTS',['PK id; FK employee_id','name; relationship','phone'])
    d.entity(720,329,225,'EMPLOYEE_TIMELINE',['PK id; FK employee_id','event_type; event_date','title'])
    d.entity(350,443,233,'ASSET_ASSIGNMENTS',['PK id; FK employee_id','FK asset_id','assigned_on; returned_on'])
    d.entity(710,462,235,'ASSETS',['PK id; UQ asset_tag','FK current_employee_id','name; status'])
    d.text(17,503,'Manager box repeats EMPLOYEES',16)
    d.text(17,525,'to show its self-reference.',16)
    d.text(17,553,'Selected Core HR entities',17,True)
    d.save()

    d=Drawing('dfd-context',960,212)
    d.box(8,52,245,100,'HR staff / Managers\nEmployees',size=20)
    d.box(362,35,255,135,'0\nHRGenius\nCore HR system','ellipse',21)
    d.box(771,67,181,70,'Mail service',size=20)
    d.line(253,80,362,80,True); d.text(307,38,'Credentials /',16,center=True); d.text(307,57,'HR requests',16,center=True)
    d.line(362,137,253,137,True); d.text(303,149,'Tokens /',16,center=True); d.text(303,169,'permitted results',16,center=True)
    d.line(617,103,771,103,True); d.text(694,60,'Expiry digest',17,center=True)
    d.save()

    d=Drawing('dfd-level-1',960,390)
    for x in (10,343,676): d.box(x,4,270,43,'HR staff / Managers / Employees',size=15)
    for x,label in [(10,'1.0\nAuthenticate user'),(343,'2.0\nMaintain Core HR'),(676,'3.0\nManage documents / expiry')]:
        d.box(x,124,270,80,label,'ellipse',18)
    for x,l1,l2 in [(145,'Credentials','Tokens / status'),(478,'HR requests','Permitted results'),(811,'Files / requests','Files / results')]:
        d.line(x-115,47,x-115,144,True); d.line(x+115,144,x+115,47,True)
        d.text(x,61,l1,14,center=True); d.text(x,94,l2,14,center=True)
    d.store(10,275,270,'D1 Users / roles / tokens')
    d.store(343,275,270,'D2 Employee / org / assets')
    d.store(676,275,270,'D3 Document metadata / files')
    for x,label in [(145,'Identity / session data'),(478,'Core HR records'),(811,'Metadata / files')]:
        d.line(x-95,193,x-95,275,True,True); d.text(x-78,232,label,14)
    d.store(343,350,270,'D4 Audit / timeline')
    d.line(362,191,316,214);d.line(316,214,316,368);d.line(316,368,343,368,True)
    d.text(210,323,'Change events',15)
    d.line(704,197,642,220);d.line(642,220,642,368);d.line(642,368,613,368,True)
    d.text(648,324,'Document events',15)
    d.box(698,345,240,40,'Mail service',size=17)
    d.line(946,164,955,164);d.line(955,164,955,365);d.line(955,365,938,365,True)
    d.text(846,316,'Expiry digest',15)
    d.save()

# Basic Helvetica metrics for deterministic PDF wrapping.
WIDTHS={}
for chars,widths in [('ABCDEFGHIJKLMNOPQRSTUVWXYZ',[667,667,722,722,667,611,778,722,278,500,667,556,833,722,778,667,778,722,667,611,722,667,944,667,667,611]),('abcdefghijklmnopqrstuvwxyz',[556,556,500,556,556,278,556,556,222,222,500,222,833,556,556,556,556,333,500,278,556,500,722,500,500,500])]:
    WIDTHS.update(dict(zip(chars,widths)))
WIDTHS.update({c:556 for c in '0123456789'})
WIDTHS.update({' ':278,'.':278,',':278,':':278,';':278,'!':278,'-':333,'/':278,'\\':278,'(':333,')':333,'[':278,']':278,'|':260,'*':389,'+':584,'=':584,'_':556,'?':556,"'":191,'"':355,'&':667,'@':1015,'<':584,'>':584})
def clean(s): return re.sub(r'\*\*|`','',s)
def width(s,size=11,bold=False): return sum(WIDTHS.get(c,600) for c in s)*size/1000*(1.025 if bold else 1)
def wrap(s,limit,size=11,bold=False):
    out=[]; line=''
    for word in s.split():
        if width((line+' '+word).strip(),size,bold)>limit and line: out.append(line);line=''
        line=(line+' '+word).strip()
    if line:out.append(line)
    return out
def rgb(h): return ' '.join(f'{int(h[i:i+2],16)/255:.3f}' for i in (1,3,5))
def ps(s): return s.replace('\\','\\\\').replace('(','\\(').replace(')','\\)')

class PDF:
    def __init__(self): self.pages=[];self.ops=[];self.y=0
    def text(self,x,y,t,size=11,bold=False,color=INK):
        self.ops.append(f'BT /{"F2" if bold else "F1"} {size:.2f} Tf {rgb(color)} rg 1 0 0 1 {x:.2f} {841.89-y-size*.82:.2f} Tm ({ps(t)}) Tj ET')
    def line(self,x1,y1,x2,y2,color=INK):self.ops.append(f'{rgb(color)} RG 0.65 w {x1:.2f} {841.89-y1:.2f} m {x2:.2f} {841.89-y2:.2f} l S')
    def rect(self,x,y,w,h,fill=PALE):self.ops.append(f'{rgb(fill)} rg {rgb(INK)} RG 0.6 w {x:.2f} {841.89-y-h:.2f} {w:.2f} {h:.2f} re B')
    def diagram(self,path):
        obj=json.loads(path.with_suffix('.json').read_text())
        scale=483/obj['width']; oy=self.y
        for s in obj['shapes']:
            k=s['kind']
            if k=='text':
                size=s['size']*scale;x=56+s['x']*scale
                if s['center']:x-=width(s['text'],size,s['bold'])/2
                self.text(x,oy+s['y']*scale,s['text'],size,s['bold'])
            elif k=='line':self.line(56+s['x1']*scale,oy+s['y1']*scale,56+s['x2']*scale,oy+s['y2']*scale)
            elif k=='rect':self.rect(56+s['x']*scale,oy+s['y']*scale,s['w']*scale,s['h']*scale,s['fill'])
            elif k=='polygon':
                points=[(56+x*scale,841.89-oy-y*scale) for x,y in s['points']]
                self.ops.append(f'{rgb(s["fill"])} rg {rgb(INK)} RG '+f'{points[0][0]:.2f} {points[0][1]:.2f} m '+' '.join(f'{x:.2f} {y:.2f} l' for x,y in points[1:])+' h B')
            elif k=='ellipse':
                x=56+(s['x']+s['w']/2)*scale;y=841.89-oy-(s['y']+s['h']/2)*scale
                a=s['w']*scale/2;b=s['h']*scale/2;c=.55228475
                self.ops.append(f'{rgb(s["fill"])} rg {rgb(INK)} RG {x+a:.2f} {y:.2f} m '+
                    f'{x+a:.2f} {y+b*c:.2f} {x+a*c:.2f} {y+b:.2f} {x:.2f} {y+b:.2f} c '+
                    f'{x-a*c:.2f} {y+b:.2f} {x-a:.2f} {y+b*c:.2f} {x-a:.2f} {y:.2f} c '+
                    f'{x-a:.2f} {y-b*c:.2f} {x-a*c:.2f} {y-b:.2f} {x:.2f} {y-b:.2f} c '+
                    f'{x+a*c:.2f} {y-b:.2f} {x+a:.2f} {y-b*c:.2f} {x+a:.2f} {y:.2f} c B')
        self.y+=obj['height']*scale+5
    def para(self,s,size=10.5,bold=False,gap=6):
        for line in wrap(clean(s),483,size,bold):self.text(56,self.y,line,size,bold);self.y+=size*1.2
        self.y+=gap
    def table(self,rows):
        n=len(rows[0]); proportions=([.16,.84] if rows[0][0]=='Page' else ([.25,.75] if rows[0][0]=='Quality requirement' else [.43,.57])) if n==2 else [.14,.4,.46]
        widths=[483*p for p in proportions]
        for ri,row in enumerate(rows):
            lines=[wrap(clean(t),w-12,9.4,ri==0) for t,w in zip(row,widths)]
            h=max(len(t) for t in lines)*12+8;x=56
            for w,ls in zip(widths,lines):
                self.rect(x,self.y,w,h,PALE if ri==0 else '#ffffff')
                for j,t in enumerate(ls):self.text(x+6,self.y+5+j*12,t,9.4,ri==0)
                x+=w
            self.y+=h
        self.y+=9
    def save(self,path):
        objects=[b'',b'',b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>',b'<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>']
        ids=[]
        for ops in self.pages:
            stream='\n'.join(ops).encode('cp1252');sid=len(objects)+1
            objects.append(f'<< /Length {len(stream)} >>\nstream\n'.encode()+stream+b'\nendstream')
            pid=len(objects)+1;ids.append(pid)
            objects.append(f'<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595.28 841.89] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents {sid} 0 R >>'.encode())
        objects[0]=b'<< /Type /Catalog /Pages 2 0 R >>'
        objects[1]=f'<< /Type /Pages /Count {len(ids)} /Kids [{" ".join(str(i)+" 0 R" for i in ids)}] >>'.encode()
        result=bytearray(b'%PDF-1.4\n%\xe2\xe3\xcf\xd3\n');offsets=[0]
        for i,o in enumerate(objects,1):offsets.append(len(result));result.extend(f'{i} 0 obj\n'.encode()+o+b'\nendobj\n')
        start=len(result);result.extend(f'xref\n0 {len(objects)+1}\n0000000000 65535 f \n'.encode())
        for o in offsets[1:]:result.extend(f'{o:010} 00000 n \n'.encode())
        result.extend(f'trailer\n<< /Size {len(objects)+1} /Root 1 0 R >>\nstartxref\n{start}\n%%EOF'.encode())
        path.write_bytes(result)

def blocks(page):
    lines=page.strip().splitlines();i=0
    while i<len(lines):
        s=lines[i].strip();i+=1
        if not s:continue
        if s.startswith('|'):
            rows=[]
            while True:
                if not re.match(r'^\|[\s:|\-]+\|$',s): rows.append([c.strip() for c in s.strip('|').split('|')])
                if i>=len(lines) or not lines[i].startswith('|'):break
                s=lines[i];i+=1
            yield 'table',rows
        elif s.startswith('!['):yield 'image',re.match(r'!\[(.*?)\]\((.*?)\)',s).groups()
        elif s.startswith('#'):yield 'h'+str(len(s)-len(s.lstrip('#'))),s.lstrip('# ')
        else:yield 'p',s

def run(text,bold=False,size=None):
    prop=('<w:b/>' if bold else '')+(f'<w:sz w:val="{size}"/>' if size else '')
    return f'<w:r><w:rPr>{prop}</w:rPr><w:t xml:space="preserve">{xe(text)}</w:t></w:r>'
def paragraph(text,style=None):
    parts=re.split(r'(\*\*.*?\*\*)',text)
    rs=''.join(run(clean(p),p.startswith('**')) for p in parts)
    return '<w:p><w:pPr>'+(f'<w:pStyle w:val="{style}"/>' if style else '')+'</w:pPr>'+rs+'</w:p>'

def build():
    content=(ROOT/'HRGenius_Project_Report.md').read_text(encoding='utf-8')
    pages=content.split('<!-- PAGE -->');assert len(pages)==12
    pdf=PDF();doc=[];rels=[];media=[];htmlpages=[]
    for pno,page in enumerate(pages,1):
        pdf.ops=[];pdf.y=64
        pdf.text(56,28,'HRGENIUS  |  HUMAN RESOURCE MANAGEMENT SYSTEM',8,True)
        pdf.line(56,44,539,44,BLUE)
        html=[]
        for kind,value in blocks(page):
            if kind.startswith('h'):
                lev=int(kind[1:]);size=18 if lev==1 else 12
                if lev==1:pdf.y+=3
                pdf.para(value,size,True,10 if lev==1 else 6)
                title=paragraph(value,'Title' if lev==1 else 'Heading1')
                if lev==1 and pno>1:title=title.replace('<w:pPr>','<w:pPr><w:pageBreakBefore/>')
                doc.append(title)
                html.append(f'<h{lev}>{escape(value)}</h{lev}>')
            elif kind=='p':
                pdf.para(value)
                doc.append(paragraph(value));html.append('<p>'+escape(clean(value))+'</p>')
            elif kind=='table':
                pdf.table(value)
                n=len(value[0]);proportions=([.16,.84] if value[0][0]=='Page' else ([.25,.75] if value[0][0]=='Quality requirement' else [.43,.57])) if n==2 else [.14,.4,.46]
                ws=[int(9660*x) for x in proportions]
                tbl='<w:tbl><w:tblPr><w:tblW w:w="9660" w:type="dxa"/><w:tblBorders>'+''.join(f'<w:{e} w:val="single" w:sz="4" w:color="C4D2DE"/>' for e in ['top','left','bottom','right','insideH','insideV'])+'</w:tblBorders><w:tblCellMar><w:top w:w="60" w:type="dxa"/><w:left w:w="90" w:type="dxa"/><w:bottom w:w="60" w:type="dxa"/><w:right w:w="90" w:type="dxa"/></w:tblCellMar></w:tblPr><w:tblGrid>'+''.join(f'<w:gridCol w:w="{w}"/>' for w in ws)+'</w:tblGrid>'
                for ri,row in enumerate(value):
                    tbl+='<w:tr><w:trPr><w:cantSplit/>'+('<w:tblHeader/>' if ri==0 else '')+'</w:trPr>'
                    for w,t in zip(ws,row):tbl+=f'<w:tc><w:tcPr><w:tcW w:w="{w}" w:type="dxa"/>'+('<w:shd w:fill="EDF4F9"/>' if ri==0 else '')+'</w:tcPr><w:p><w:pPr><w:spacing w:after="30" w:line="230" w:lineRule="auto"/></w:pPr>'+run(clean(t),ri==0,19)+'</w:p></w:tc>'
                    tbl+='</w:tr>'
                doc.append(tbl+'</w:tbl>')
                html.append('<table>'+''.join('<tr>'+''.join(f'<td>{escape(clean(c))}</td>' for c in r)+'</tr>' for r in value)+'</table>')
            elif kind=='image':
                caption,filename=value;path=ROOT/filename;pdf.diagram(path);pdf.para(caption,9,False,7)
                obj=json.loads(path.with_suffix('.json').read_text());cx=6134100;cy=int(cx*obj['height']/obj['width']);idx=len(media)+1;rid=f'rIdImg{idx}'
                media.append(path);rels.append(f'<Relationship Id="{rid}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/{path.name}"/>')
                doc.append(f'<w:p><w:pPr><w:spacing w:after="30"/></w:pPr><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0"><wp:extent cx="{cx}" cy="{cy}"/><wp:docPr id="{idx}" name="{xe(caption)}"/><a:graphic><a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture"><pic:pic><pic:nvPicPr><pic:cNvPr id="{idx}" name="{path.name}"/><pic:cNvPicPr/></pic:nvPicPr><pic:blipFill><a:blip r:embed="{rid}"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill><pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="{cx}" cy="{cy}"/></a:xfrm><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr></pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>')
                doc.append(paragraph(caption,'Caption'));html.append(f'<img src="{filename}"/><p class="caption">{escape(caption)}</p>')
        print(f'Page {pno}: bottom={pdf.y:.1f}; words={len(page.split())}')
        if pdf.y>786:raise ValueError(f'Page {pno} overflows: {pdf.y}')
        pdf.line(56,801,539,801,BLUE);pdf.text(56,812,'DETAILED PROJECT REPORT',8);pdf.text(472,812,f'{pno} / 12',8)
        pdf.pages.append(pdf.ops)
        htmlpages.append('<section>'+''.join(html)+f'<footer>HRGenius | {pno} / 12</footer></section>')
    pdf.save(ROOT/'HRGenius_Project_Report.pdf')
    ns='xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture"'
    sect='<w:sectPr><w:headerReference w:type="default" r:id="rIdHeader"/><w:footerReference w:type="default" r:id="rIdFooter"/><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1120" w:right="1120" w:bottom="1000" w:left="1120" w:header="480" w:footer="480"/></w:sectPr>'
    styles='''<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:docDefaults><w:rPrDefault><w:rPr><w:rFonts w:ascii="Arial" w:hAnsi="Arial"/><w:sz w:val="21"/><w:color w:val="17324D"/></w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after="100" w:line="252" w:lineRule="auto"/></w:pPr></w:pPrDefault></w:docDefaults><w:style w:type="paragraph" w:default="1" w:styleId="Normal"><w:name w:val="Normal"/></w:style><w:style w:type="paragraph" w:styleId="Title"><w:name w:val="Title"/><w:pPr><w:keepNext/><w:spacing w:before="60" w:after="160"/></w:pPr><w:rPr><w:b/><w:sz w:val="38"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Heading1"><w:name w:val="heading 1"/><w:pPr><w:keepNext/><w:spacing w:before="90" w:after="70"/></w:pPr><w:rPr><w:b/><w:sz w:val="24"/></w:rPr></w:style><w:style w:type="paragraph" w:styleId="Caption"><w:name w:val="caption"/><w:pPr><w:spacing w:after="90"/></w:pPr><w:rPr><w:sz w:val="18"/></w:rPr></w:style></w:styles>'''
    relbase='http://schemas.openxmlformats.org/officeDocument/2006/relationships/'
    rels.extend(f'<Relationship Id="rId{name.title()}" Type="{relbase}{name}" Target="{name}.xml"/>' for name in ('styles','header','footer'))
    types='<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Default Extension="png" ContentType="image/png"/>'+''.join(f'<Override PartName="/word/{name}.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.{kind}+xml"/>' for name,kind in [('document','document.main'),('styles','styles'),('header','header'),('footer','footer')])+'</Types>'
    with zipfile.ZipFile(ROOT/'HRGenius_Project_Report.docx','w',zipfile.ZIP_DEFLATED) as z:
        z.writestr('[Content_Types].xml',types)
        z.writestr('_rels/.rels',f'<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="{relbase}officeDocument" Target="word/document.xml"/></Relationships>')
        z.writestr('word/document.xml',f'<?xml version="1.0" encoding="UTF-8"?><w:document {ns}><w:body>'+''.join(doc)+sect+'</w:body></w:document>')
        z.writestr('word/styles.xml',styles)
        z.writestr('word/_rels/document.xml.rels','<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'+''.join(rels)+'</Relationships>')
        z.writestr('word/header.xml',f'<w:hdr {ns}>'+paragraph('HRGENIUS | HUMAN RESOURCE MANAGEMENT SYSTEM','Caption')+'</w:hdr>')
        z.writestr('word/footer.xml',f'<w:ftr {ns}><w:p><w:pPr><w:jc w:val="right"/></w:pPr>'+run('HRGenius | Page ',size=17)+'<w:fldSimple w:instr="PAGE"/></w:p></w:ftr>')
        for p in media:z.write(p,'word/media/'+p.name)
    css='@page{size:A4;margin:0}body{background:#ddd;color:#17324d;font:11pt Arial}section{box-sizing:border-box;width:210mm;min-height:297mm;margin:12px auto;padding:18mm 20mm;background:white;position:relative;page-break-after:always}h1{font-size:20pt}h2,h3{font-size:12pt;margin:12px 0 7px}p{line-height:1.3;margin:0 0 9px}table{border-collapse:collapse;width:100%;font-size:9.4pt;margin-bottom:10px}td{border:1px solid #c4d2de;padding:5px}tr:first-child{background:#edf4f9;font-weight:bold}img{width:100%}.caption{font-size:9pt}footer{font-size:8pt;margin-top:18px;text-align:right}@media print{body{margin:0;background:white}section{margin:0;height:297mm;min-height:0}}'
    (ROOT/'HRGenius_Project_Report.html').write_text('<!doctype html><html><head><meta charset="utf-8"><title>HRGenius Project Report</title><style>'+css+'</style></head><body>'+''.join(htmlpages)+'</body></html>',encoding='utf-8')

if __name__=='__main__':
    diagrams()
    if '--prepare' not in sys.argv:build()
