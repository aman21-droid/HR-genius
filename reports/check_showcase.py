from pathlib import Path
import sys, os, zipfile, json
sys.path.insert(0,str(Path(os.environ['TEMP'])/'hrgenius-ppt-tools'))
import fitz
from PIL import Image, ImageDraw
from lxml import etree
p=Path(__file__).parent
z=zipfile.ZipFile(p/'HRGenius_Interactive_Showcase.pptx')
internal=external=0
for name in z.namelist():
    if name.startswith('ppt/slides/_rels/'):
        for rel in etree.fromstring(z.read(name)):
            internal+=rel.get('Type','').endswith('/slide')
            external+='localhost:4200' in rel.get('Target','')
print('Internal navigation:',internal,'Live app links:',external)
d=fitz.open(p/'HRGenius_Interactive_Showcase.pdf')
canvas=Image.new('RGB',(1280,4*204),'#dddcd5'); draw=ImageDraw.Draw(canvas)
for i,page in enumerate(d):
    pix=page.get_pixmap(matrix=fitz.Matrix(.4,.4),alpha=False)
    im=Image.frombytes('RGB',[pix.width,pix.height],pix.samples)
    im.thumbnail((312,176)); x=(i%4)*320;y=(i//4)*204
    canvas.paste(im,(x,y));draw.text((x+5,y+178),str(i+1),fill='black')
canvas.save(p/'HRGenius_Slide_Preview.png')
print('Rendered PDF pages:',len(d))
