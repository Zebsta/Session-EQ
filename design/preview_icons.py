"""Preview the actual vector paths under launcher masks, without font glyph icons."""
from pathlib import Path
import xml.etree.ElementTree as ET
import html
import gi
import cairo

gi.require_version('Rsvg', '2.0')
from gi.repository import Rsvg

root = Path(__file__).resolve().parent
res = root.parent / 'app/src/main/res'
ns = '{http://schemas.android.com/apk/res/android}'

def vector_paths(name, fill=None):
    paths = []
    def render(node):
        if node.tag == 'group':
            px,py=node.get(ns+'pivotX','0'),node.get(ns+'pivotY','0')
            tx,ty=node.get(ns+'translateX','0'),node.get(ns+'translateY','0')
            sx,sy=node.get(ns+'scaleX','1'),node.get(ns+'scaleY','1')
            transform=f'translate({tx} {ty}) translate({px} {py}) scale({sx} {sy}) translate({-float(px)} {-float(py)})'
            return '<g transform="'+transform+'">'+''.join(render(child) for child in node)+'</g>'
        if node.tag != 'path': return ''.join(render(child) for child in node)
        path=node
        attrs = {'d': path.get(ns + 'pathData')}
        for android, svg in [('fillColor','fill'),('strokeColor','stroke'),('strokeWidth','stroke-width')]:
            value = path.get(ns + android)
            if value:
                if value == '#00000000': value = 'none'
                elif value.startswith('@color/'): value = fill or '#252525'
                elif fill and value == '#FFFFFF': value = fill
                attrs[svg] = value
        return '<path ' + ' '.join(f'{k}="{html.escape(v)}"' for k,v in attrs.items()) + '/>'
    return render(ET.parse(res / 'drawable' / name).getroot())

logo = vector_paths('ic_logo_foreground.xml')
tune = vector_paths('ic_tune.xml', '#FFFFFF')
ring = '<circle cx="54" cy="54" r="28.5" fill="none" stroke="white" stroke-width="3"/>'
tune_logo = ring + '<g transform="translate(36 36) scale(1.5)">' + tune + '</g>'

parts = ['<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="640" viewBox="0 0 1120 640">',
         '<rect width="1120" height="640" fill="#E9E6E0"/>']

def text(s,x,y,size,fill,weight='400'):
    parts.append(f'<text x="{x}" y="{y}" font-family="DejaVu Sans,sans-serif" font-size="{size}" font-weight="{weight}" fill="{fill}">{html.escape(s)}</text>')

for i, (bg,fg,muted,orange) in enumerate([('#F5F4F1','#252525','#68645E','#ED8700'),('#383838','#F0F0F0','#B0B0B0','#C96E00')]):
    x = 20+i*550
    parts.append(f'<rect x="{x}" y="20" width="530" height="600" rx="24" fill="{bg}"/>')
    text('Светлая тема' if i==0 else 'Тёмная тема',x+28,65,23,fg,'600')
    for j,(paths,label) in enumerate([(logo,'Регулировки'),(logo,'Круглая маска')]):
        xx=x+48+j*242
        parts.append(f'<svg x="{xx}" y="100" width="160" height="160" viewBox="18 18 72 72">')
        radius=18 if j==0 else 36
        parts.append(f'<defs><clipPath id="mask{i}{j}"><rect x="18" y="18" width="72" height="72" rx="{radius}"/></clipPath></defs>')
        parts.append(f'<g clip-path="url(#mask{i}{j})"><rect x="18" y="18" width="72" height="72" fill="{orange}"/>{paths}</g></svg>')
        text(label,xx-1,292,18,fg)
    text('Тематическая иконка Android',x+28,350,17,muted)
    parts.append(f'<svg x="{x+48}" y="370" width="80" height="80" viewBox="18 18 72 72"><rect x="18" y="18" width="72" height="72" rx="36" fill="'+('#E4D3B8' if i==0 else '#53432C')+'"/>'+vector_paths('ic_logo_foreground.xml', '#543300' if i==0 else '#FFDAA0')+'</svg>')
    text('Иконки панели и уведомлений',x+28,503,17,muted)
    for j,name in enumerate(['ic_tune.xml','ic_settings.xml']):
        parts.append(f'<svg x="{x+48+j*75}" y="530" width="32" height="32" viewBox="0 0 24 24">'+vector_paths(name,fg)+'</svg>')
    parts.append(f'<svg x="{x+200}" y="526" width="40" height="40" viewBox="18 18 72 72">'+vector_paths('ic_logo_foreground.xml',fg)+'</svg>')
    text('Векторные, без символов шрифта',x+28,595,15,muted)

parts.append('</svg>')
svg=''.join(parts)
(root/'icons-preview.svg').write_text(svg)
surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,1120,640)
context=cairo.Context(surface)
handle=Rsvg.Handle.new_from_data(svg.encode())
rect=Rsvg.Rectangle();rect.x=0;rect.y=0;rect.width=1120;rect.height=640
handle.render_document(context,rect)
surface.write_to_png(str(root/'icons-preview.png'))
print(root/'icons-preview.png')
