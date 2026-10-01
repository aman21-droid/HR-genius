"""Diagram-led redesign. All illustrations are editable PowerPoint shapes."""
import build_showcase as b
from pptx.enum.shapes import MSO_SHAPE
from pptx.util import Inches, Pt
from pptx.oxml.xmlchemy import OxmlElement
import math

s=b.slides
def shape(sl,kind,x,y,w,h,fill='olive'):
    z=sl.shapes.add_shape(kind, Inches(x), Inches(y), Inches(w), Inches(h))
    z.fill.solid();z.fill.fore_color.rgb=b.rgb(fill);z.line.fill.background()
    z._element.spPr.append(OxmlElement('a:effectLst'))
    return z
def dot(sl,x,y,w,fill='olive'): return shape(sl,MSO_SHAPE.OVAL,x,y,w,w,fill)
def icon(sl,name,x,y,k=.65,color='olive'):
    def r(a,c,w,h): return b.rect(sl,x+a*k,y+c*k,w*k,h*k,color,False)
    def o(a,c,w): return dot(sl,x+a*k,y+c*k,w*k,color)
    def l(a,c,d,e): return b.line(sl,x+a*k,y+c*k,x+d*k,y+e*k,color)
    if name=='people':
        o(.35,0,.3);o(.03,.18,.23);o(.74,.18,.23)
        r(.28,.38,.44,.48);r(0,.48,.22,.3);r(.78,.48,.22,.3)
    elif name=='chart':
        r(.08,.56,.18,.36);r(.38,.3,.18,.62);r(.68,.06,.18,.86)
    elif name=='check':
        l(.1,.49,.38,.78);l(.38,.78,.9,.12)
    elif name=='calendar':
        r(.04,.16,.88,.1);l(.04,.16,.04,.9);l(.92,.16,.92,.9);l(.04,.9,.92,.9)
        l(.26,0,.26,.3);l(.7,0,.7,.3)
        for a in [.2,.45,.7]:
            for c in [.42,.67]: r(a,c,.1,.1)
    elif name=='shield':
        shape(sl,MSO_SHAPE.PENTAGON,x+.12*k,y+.08*k,.76*k,.85*k,color).rotation=180
        b.line(sl,x+.29*k,y+.42*k,x+.46*k,y+.58*k,'white');b.line(sl,x+.46*k,y+.58*k,x+.72*k,y+.3*k,'white')
    elif name=='money':
        o(.02,.02,.92);t=b.txt(sl,x+.12*k,y+.13*k,.73*k,.64*k,'$',int(34*k),'white',True);t.text_frame.paragraphs[0].alignment=1
    elif name=='star': shape(sl,MSO_SHAPE.STAR_5_POINT,x,y,k,k,color)
    elif name=='screen':
        for a,c,d,e in [(0,.05,.95,.05),(.95,.05,.95,.7),(.95,.7,0,.7),(0,.7,0,.05),(.47,.7,.47,.9),(.22,.9,.72,.9)]:l(a,c,d,e)
    elif name=='database': shape(sl,MSO_SHAPE.CAN,x+.1*k,y,.8*k,k,color)
    elif name=='flow':
        r(.33,0,.33,.25);r(0,.65,.33,.25);r(.65,.65,.33,.25)
        l(.49,.25,.49,.48);l(.16,.48,.82,.48);l(.16,.48,.16,.65);l(.82,.48,.82,.65)
    elif name=='file':
        shape(sl,MSO_SHAPE.FOLDED_CORNER,x+.1*k,y,.75*k,k,color)
        for yy in [.35,.51,.67]:b.line(sl,x+.25*k,y+yy*k,x+.65*k,y+yy*k,'white')
    elif name=='chat': shape(sl,MSO_SHAPE.ROUNDED_RECTANGULAR_CALLOUT,x,y,k,.8*k,color)
    elif name=='play': shape(sl,MSO_SHAPE.ISOSCELES_TRIANGLE,x+.15*k,y+.06*k,.7*k,.8*k,color).rotation=90
    else: icon(sl,'check',x,y,k,color)
def btn(sl,x,y,w,label,target,kind='play',dark=False):
    z=b.button(sl,x,y,w,label,target,fill='lime' if dark else 'olive',color='deep' if dark else 'white')
    z.height=Inches(.55);z.adjustments[0]=.5
    return z
def fresh(n,kicker,title,sub='',dark=False):
    sl=s[n-1]
    for sh in list(sl.shapes): sl.shapes._spTree.remove(sh._element)
    for el in list(sl._element):
        if el.tag.endswith('}transition'): sl._element.remove(el)
    b.base(n,kicker,title,sub,dark)
    # Suppress theme-inherited shadows throughout this rebuild.
    return sl
def label(sl,x,y,w,title,detail,ic='check'):
    dot(sl,x,y,.56,'pale');icon(sl,ic,x+.12,y+.12,.33)
    b.txt(sl,x+.76,y,w-.76,.36,title,19,'ink',True)
    b.txt(sl,x+.76,y+.48,w-.76,.62,detail,14,'muted')

# Cover: a connected HR ecosystem, not decorative photography.
sl=fresh(1,'People • process • insight','',dark=True)
b.txt(sl,.7,1.25,6,.8,'HRGenius',48,'white',True)
b.txt(sl,.73,2.45,5.7,1.6,'Every workday.\nA little more human.',34,'white',False,'Georgia')
b.txt(sl,.75,4.42,5.4,.8,'An employee-centered system that connects\npeople, decisions and everyday progress.',18,'lime')
btn(sl,.75,5.65,2.6,'Start exploring   →',2,dark=True)
cx,cy=9.55,3.7
nodes=[('people','People',8.95,1.22),('calendar','Time',11.1,2.9),('chart','Insight',10.4,5.0),('star','Growth',7.7,5.0),('money','Pay',7.0,2.9)]
for ic,lab,x,y in nodes: b.line(sl,cx,cy,x+.47,y+.45,'olive')
dot(sl,8.65,2.83,1.8,'lime');icon(sl,'people',9.08,3.13,.95,'deep')
for ic,lab,x,y in nodes:
    dot(sl,x,y,.9,'pale');icon(sl,ic,x+.2,y+.18,.5)
    b.txt(sl,x-.2,y+1.02,1.35,.3,lab,14,'lime',True)

# Hub: numbered path with deliberate illustrated chapter buttons.
sl=fresh(2,'Your story map','A clear path from problem to product.','Choose a chapter. Every olive button is clickable in Slide Show mode.')
chapters=[('people','01','Why it matters','The problem + people',3),('screen','02','See the experience','Design + daily interactions',4),('flow','03','Follow the work','Modules + a request journey',5),('database','04','Look underneath','Architecture + trust',7),('check','05','See the evidence','Build + interaction checks',9),('play','06','Try a moment','Pick your mini-demo',10)]
for i,(ic,no,title,desc,target) in enumerate(chapters):
    x=.7+(i%3)*4.12;y=2.45+(i//3)*2.15
    b.rect(sl,x,y,3.85,1.95,'white');icon(sl,ic,x+.2,y+.22,.55)
    b.txt(sl,x+.97,y+.17,2.6,.32,no+' / '+title,16,'ink',True)
    b.txt(sl,x+.97,y+.66,2.6,.5,desc,13,'muted')
    btn(sl,x+.97,y+1.21,2.5,'Explore chapter   →',target)

# Purpose: explicitly map problems to outcomes.
sl=fresh(3,'Why this project','Turn scattered HR work into a shared flow.','Three common problems. One connected workplace.')
for i,(ic,left,right,detail) in enumerate([('file','Scattered records','One employee context','Profiles, documents and reporting lines'),('chat','Repeated follow-ups','A visible next step','Requests, ownership and status'),('chart','Hard-to-read data','Useful daily insight','Workforce trends and readable values')]):
    y=2.45+i*1.33
    icon(sl,ic,.8,y+.19,.55,'muted');b.txt(sl,1.65,y+.26,3.1,.46,left,21)
    shape(sl,MSO_SHAPE.CHEVRON,5.0,y+.28,.55,.45,'olive')
    b.rect(sl,6.02,y,6.57,1.09,'white');icon(sl,'check',6.28,y+.26,.42)
    b.txt(sl,7.0,y+.14,5.2,.35,right,21,'olive',True);b.txt(sl,7.,y+.64,5.2,.3,detail,14,'muted')

# Product: editable interface anatomy (explicitly schematic), one real screenshot in appendix.
sl=fresh(4,'Experience anatomy','A dashboard with a reason for every element.','Editable interface diagram · illustrative layout, not live employee data')
b.rect(sl,.65,2.42,7.45,4.15,'white',line='line');b.rect(sl,.65,2.42,1.22,4.15,'deep')
icon(sl,'people',.97,2.7,.5,'lime')
for i in range(5): b.rect(sl,.85,3.53+i*.43,.82,.1,'lime' if i==0 else 'olive')
b.rect(sl,2.08,2.67,5.78,.85,'pale');b.txt(sl,2.28,2.84,5.3,.4,'Good morning. What’s next?',22,'olive',True)
for i,(ic,t) in enumerate([('people','People'),('calendar','Leave'),('money','Pay')]):
    x=2.09+i*1.98;b.rect(sl,x,3.75,1.8,.75,'bg');icon(sl,ic,x+.13,3.9,.33);b.txt(sl,x+.63,3.98,1.1,.27,t,13,'ink',True)
b.rect(sl,2.1,4.75,3.34,1.51,'bg');b.txt(sl,2.27,4.91,2.95,.3,'Workforce view',13,'ink',True)
for i,v in enumerate([.25,.42,.36,.6,.72]):b.rect(sl,2.38+i*.5,6.03-v,.23,v,'olive',False)
b.rect(sl,5.67,4.75,2.18,1.51,'pale');icon(sl,'star',5.9,4.97,.35);b.txt(sl,5.9,5.55,1.76,.49,'Give a shoutout',15,'olive',True)
for y,ic,t,d in [(2.58,'people','Human first','A greeting and relevant next actions.'),(3.85,'chart','Readable insight','Switch periods; inspect values or a table.'),(5.12,'star','Everyday appreciation','Celebrate a teammate with real feedback.')]:label(sl,8.48,y,4.08,t,d,ic)
btn(sl,9.2,6.31,3.45,'View the real UI + dark mode  →',11)

# Journey: alternating process ribbon with stage symbols.
sl=fresh(5,'The employee lifecycle','Six connected stages. One employee journey.','Follow the arrows through the product’s functional scope.')
positions=[(.73,2.65),(4.87,2.65),(9.0,2.65),(9.,4.85),(4.87,4.85),(.73,4.85)]
for a,c in [(0,1),(1,2),(2,3),(3,4),(4,5)]:
    x,y=positions[a];xx,yy=positions[c]
    if y==yy: shape(sl,MSO_SHAPE.CHEVRON,min(x,xx)+3.65,y+.48,.35,.42,'olive').rotation=0 if xx>x else 180
    else: shape(sl,MSO_SHAPE.CHEVRON,x+1.4,y+1.64,.36,.42,'olive').rotation=90
for i,((x,y),(ic,title,body)) in enumerate(zip(positions,[('people','Attract & welcome','Careers · interviews · offers\nOnboarding plans and tasks'),('file','Know your people','Profiles · org chart · files\nOrganization masters · assets'),('calendar','Manage the day','Attendance · regularization\nLeave balances · approvals'),('money','Pay with structure','Components · runs · adjustments\nPayslips and exports'),('star','Grow & recognize','Goals · reviews · progress\nPraise and feedback'),('chat','Support & understand','Tickets · policies · acknowledgements\nAnalytics and personal summary')])):
    b.rect(sl,x,y,3.53,1.55,'white');icon(sl,ic,x+.18,y+.18,.44)
    b.txt(sl,x+.78,y+.16,2.57,.35,str(i+1)+'. '+title,17,'olive',True);b.txt(sl,x+.19,y+.8,3.18,.62,body,13,'muted')
btn(sl,9.37,6.39,3.15,'Technical module catalogue  →',12)

# Decision flow: show actual logical branching, not generic tiles.
sl=fresh(6,'A request journey','Make the next step obvious.','Example: an employee requests leave. Policy and permissions shape the outcome.')
stages=[(.72,'calendar','Request','Employee selects dates'),(3.88,'shield','Validate','Rules + balance + access'),(7.04,'people','Review','Authorized approver acts')]
for x,ic,t,d in stages:
    b.rect(sl,x,2.72,2.72,1.55,'white');icon(sl,ic,x+.2,2.91,.48);b.txt(sl,x+.87,2.96,1.65,.36,t,21,'olive',True);b.txt(sl,x+.2,3.64,2.34,.42,d,13)
for x in [3.49,6.64]:shape(sl,MSO_SHAPE.CHEVRON,x,3.25,.3,.36,'olive')
b.line(sl,9.76,3.49,10.18,3.49);b.line(sl,10.18,2.72,10.18,4.5);b.line(sl,10.18,2.72,10.58,2.72);b.line(sl,10.18,4.5,10.58,4.5)
icon(sl,'check',10.66,2.43,.4);b.txt(sl,11.2,2.42,1.52,.4,'Approved',17,'olive',True)
icon(sl,'chat',10.64,4.2,.4);b.txt(sl,11.2,4.17,1.52,.55,'Other\noutcome',15,'muted',True)
b.rect(sl,.74,5.08,11.86,1.29,'pale');icon(sl,'star',1.02,5.42,.58)
b.txt(sl,1.99,5.28,6.55,.4,'Good systems also notice good work.',22,'olive',True)
b.txt(sl,2.,5.87,6.65,.33,'A praise wall adds recognition to everyday operations.',15)
btn(sl,9.42,5.48,2.75,'Try the appreciation tour  →',10)

# Architecture: real visual symbols and a support layer.
sl=fresh(7,'How it works','A clear boundary between experience and rules.','An editable architecture diagram of the current application.')
for x,ic,title,desc in [(.85,'screen','Angular 18','UI · routes · Material\nSignals · accessible controls'),(5.03,'flow','Spring Boot','REST · validation · security\nServices · JPA repositories'),(9.22,'database','Persistence','Oracle target / H2 demo\nFlyway schema migrations')]:
    icon(sl,ic,x+.9,2.62,.98);b.txt(sl,x,3.98,3.15,.5,title,25,'olive',True);b.txt(sl,x,4.67,3.15,.85,desc,16)
for x,lab in [(3.96,'REST'),(8.15,'JPA')]:
    shape(sl,MSO_SHAPE.LEFT_RIGHT_ARROW,x,3.02,.78,.33,'olive');b.txt(sl,x-.1,3.51,1.1,.3,lab,12,'muted',True)
b.rect(sl,.8,5.78,11.8,.68,'pale');b.txt(sl,1.02,5.98,11.3,.3,'Supporting services     Files + metadata    /    Mail    /    Audit history    /    OpenAPI',16,'olive',True)

# Security: layered gates rather than equal feature cards.
sl=fresh(8,'Trust by design','Three gates before sensitive information.','The backend enforces access. The interface makes that access understandable.')
for i,(ic,t,body) in enumerate([('people','Who are you?','JWT + refresh rotation\nBCrypt + login lockout'),('shield','What may you do?','Roles + permissions\nSelf / team / HR scope'),('file','What may you see?','Selected field encryption\nMasking + controlled reveal')]):
    x=.9+i*4.13
    dot(sl,x+.88,2.5,1.12,'pale');icon(sl,ic,x+1.15,2.76,.6)
    b.txt(sl,x,3.98,3.55,.5,t,23,'olive',True);b.txt(sl,x,4.76,3.52,.85,body,18)
    if i<2:shape(sl,MSO_SHAPE.CHEVRON,x+3.42,2.84,.35,.4,'olive')
b.rect(sl,.87,6.0,11.64,.55,'deep');b.txt(sl,1.1,6.13,11.2,.26,'Cross-cutting controls: audit history, input validation and validated file handling.',14,'white')

# Evidence: geometric scoreboard, meaningful outcomes.
sl=fresh(9,'Evidence, not decoration','The experience was checked from several angles.','Recorded frontend checks in this workspace. No production certification is implied.')
for x,num,title,ic in [(.87,'36','Page / theme scans','screen'),(5.02,'19','Interaction checks','check'),(9.15,'18','Mobile routes','calendar')]:
    dot(sl,x+.7,2.51,1.78,'pale');icon(sl,ic,x+1.25,2.71,.5)
    b.txt(sl,x+1.02,3.29,1.3,.68,num,33,'olive',True)
    b.txt(sl,x,4.53,3.45,.4,title,21,'ink',True)
b.rect(sl,.87,5.34,7.4,1.1,'white');icon(sl,'check',1.08,5.66,.42);b.txt(sl,1.82,5.51,6.13,.74,'Frontend production build passed.\nZero reported automated accessibility violations.',16,'olive',True)
b.txt(sl,8.98,5.4,3.6,.92,'Next: Oracle acceptance, business-flow regression, load and recovery.',15,'muted')

# Demo: useful verbs + actual internal and external buttons.
sl=fresh(10,'Choose your interaction','What would you like to try first?','Three quick demonstrations, each with an offline fallback.',dark=True)
for x,ic,title,body,target,path in [(.8,'chart','Explore','Change the period.\nInspect an exact value.',4,'analytics'),(4.95,'star','Appreciate','Open recognition.\nShow the shoutout form.',6,'performance'),(9.08,'screen','Personalize','Compare light and dark.\nNotice readable controls.',11,'dashboard')]:
    icon(sl,ic,x+.98,2.67,.86,'lime');b.txt(sl,x,3.95,3.4,.55,title,28,'white',True);b.txt(sl,x,4.74,3.4,.8,body,18,'lime')
    btn(sl,x,5.82,1.64,'Slide tour →',target,dark=True)
    b.button(sl,x+1.8,5.82,1.65,'Open app ↗',url='http://localhost:4200/'+path,fill='white',color='deep')
b.txt(sl,.82,6.58,11.9,.27,'Live links need localhost:4200 and an authorized session. Internal slide tours work offline.',12,'lime')

# Keep screenshot only as supporting evidence: paired theme comparison.
sl=fresh(11,'Real product reference','One interface. Two considered themes.','Actual screenshots from the demonstration environment. Sample data only.')
b.pic(sl,'reports/ui-dashboard-light.png',.7,2.4,5.84,3.75)
b.pic(sl,'reports/ui-dashboard-dark.png',6.78,2.4,5.84,3.75)
btn(sl,1.61,6.27,3.78,'← Return to interface anatomy',4)
btn(sl,8.0,6.27,3.78,'Choose another interaction →',10)

# Retain detailed appendix content; add quiet section symbols.
for n,ic in [(12,'file'),(13,'database'),(14,'flow')]: icon(s[n-1],ic,12.1,.22,.36)

# Shape styles should stay flat and editable, without Office theme shadows.
for sl in s:
    for sh in sl.shapes:
        sp=sh._element.find('{http://schemas.openxmlformats.org/presentationml/2006/main}spPr')
        if sp is not None and sp.find('{http://schemas.openxmlformats.org/drawingml/2006/main}effectLst') is None:
            sp.append(OxmlElement('a:effectLst'))

# Update notes that describe the superseded screenshot-heavy layouts.
updates={1:'The opening illustration is a native editable PowerPoint diagram: people at the center, linked to time, pay, growth and insight. It introduces the system’s structure rather than a decorative photograph.',4:'This slide is now an editable schematic of interface anatomy, not a screenshot. It contains no live counts. The real light and dark dashboard screenshots are together on slide 11. Explain greeting/action hierarchy, workforce insight and appreciation, then open the real UI reference if helpful.',6:'The branching diagram summarizes a leave request: submit, validate and review, followed by an approval or another outcome. Exact policy and approval configuration determine behavior. The recognition strip demonstrates a second kind of interaction; its button opens the demo choices, not a submit action.',11:'Two actual product screenshots are shown side by side: light and dark. Use Return to interface anatomy to revisit the editable explanation or Choose another interaction to return to the demo menu.'}
for n,extra in updates.items():
    old=s[n-1].notes_slide.notes_text_frame.text
    s[n-1].notes_slide.notes_text_frame.text=extra+'\n\n'+old
b.prs.save(b.OUT/'HRGenius_Interactive_Showcase_v2.pptx')
guide=['# HRGenius — redesigned presentation','', 'Use **HRGenius_Interactive_Showcase_v2.pptx** in Slide Show mode (F5). The diagram-led redesign uses editable symbols, process arrows and clickable buttons. Slides 1–10 are the core story; 11–14 are optional references. Live app links require localhost:4200 and an authorized session.','']
for i,sl in enumerate(s):guide += [f'## Slide {i+1}','',sl.notes_slide.notes_text_frame.text,'']
(b.OUT/'HRGenius_Presenter_Notes_v2.md').write_text('\n'.join(guide),encoding='utf-8')
print('Created diagram-led v2 with 14 slides.')


