"""Render EqView's interface for the README; this is not a device screenshot."""
from pathlib import Path
import html
import json
import re
import xml.etree.ElementTree as ET
import gi
import cairo

gi.require_version('Rsvg', '2.0')
from gi.repository import Rsvg

root=Path(__file__).resolve().parent
res=root.parent/'app/src/main/res'
source=(root.parent/'app/src/main/java/dev/local/sessioneq/EqMath.java').read_text()
gains=[float(s.strip().removesuffix('f')) for s in re.search(r'ROCK = \{([^}]+)\}',source)[1].split(',')]
labels=json.loads('['+re.search(r'LABELS = \{([^}]+)\}',source)[1]+']')
ns='{http://schemas.android.com/apk/res/android}'
parts=['<svg xmlns="http://www.w3.org/2000/svg" width="1120" height="1120" viewBox="0 0 1120 1120">',
       '<rect width="1120" height="1120" rx="28" fill="#14171C"/>']

def text(value,x,y,size,color,align='middle',bold=False):
    parts.append(f'<text x="{x:g}" y="{y:g}" font-family="DejaVu Sans,sans-serif" font-size="{size:g}" font-weight="{700 if bold else 400}" text-anchor="{align}" fill="{color}">{html.escape(value)}</text>')

def line(x1,y1,x2,y2,color,width,extra=''):
    parts.append(f'<path d="M{x1:g},{y1:g}L{x2:g},{y2:g}" fill="none" stroke="{color}" stroke-width="{width}" stroke-linecap="round" {extra}/>')

def rect(x,y,w,h,color,radius=0):
    parts.append(f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" rx="{radius}" fill="{color}"/>')

def circle(x,y,r,color):
    parts.append(f'<circle cx="{x:g}" cy="{y:g}" r="{r:g}" fill="{color}"/>')

def icon(name,x,y,color):
    parts.append(f'<g transform="translate({x:g} {y:g})">')
    for path in ET.parse(res/'drawable'/name).getroot().iter('path'):
        parts.append(f'<path d="{path.get(ns+"pathData")}" fill="{color}"/>')
    parts.append('</g>')

text('Session EQ',54,61,30,'#F2F3F5','start',True)
text('20-band session equalizer',54,91,17,'#ADB4BE','start')
for index,(folder,theme_label) in enumerate([('values-night','Dark theme'),('values','Light theme')]):
    c={node.get('name').removeprefix('eq_'):node.text for node in ET.parse(res/folder/'colors.xml').getroot()}
    left=54+index*536
    text(theme_label,left,132,17,'#D5DBE2','start')
    parts.append(f'<g transform="translate({left} 154) scale(1.32)">')
    parts.append(f'<defs><clipPath id="screen{index}"><rect width="360" height="696" rx="14"/></clipPath></defs><g clip-path="url(#screen{index})">')
    h=696;bottom=h-76;step=(bottom-183)/20
    rect(0,0,360,h,c['background'])
    text('Эквалайзер',66,24,16,c['text'],'start',True);text('Rock',66,43,12,c['text'],'start')
    line(20,27,39,27,c['text'],2.5);line(20,27,30,17,c['text'],2.5);line(20,27,30,37,c['text'],2.5)
    circle(338,23,3.5,c['accent'])
    text('Баланс',180,66,10,c['text']);text('L',44,85,10,c['muted']);text('R',316,85,10,c['muted'])
    line(67,82,293,82,c['track'],7);circle(180,82,9.5,c['accent']);text('0%',180,103,10,c['muted'])
    text('Предусиление',180,119,10,c['text'])
    text('-15 dB',20,139,10,c['muted'],'start');text('+15 dB',313,139,10,c['muted'],'start')
    line(67,136,293,136,c['track'],7);circle(180,136,9.5,c['accent']);text('0.0 dB',180,159,10,c['muted'])
    parts.append(f'<rect x="53" y="167" width="254" height="{bottom-167}" fill="none" stroke="{c["border"]}" stroke-width=".5"/>')
    line(180,168,180,bottom,c['zero'],1.2,'stroke-dasharray="2 6"')
    points=[]
    for i,(gain,label) in enumerate(zip(gains,labels)):
        x=180+gain*254/30;y=176+step*(i+.5);points.append((x,y))
        text(label,41,y+3.5,10,c['muted'],'end');text(f'{gain:+.1f}',320,y+3.5,10,c['muted'],'start')
    coords=' '.join(f'{x:g},{y:g}' for x,y in points)
    parts.append(f'<polyline points="{coords}" fill="none" stroke="{c["accent"]}" stroke-width="1.3"/>')
    for x,y in points:circle(x,y,3.4,c['accent'])
    rect(0,h-69,360,69,c['toolbar'])
    rect(11,h-55,63,32,c['button_on'],18);rect(92,h-55,63,32,c['button_off'],18)
    text('ON',43,h-35,12,c['on_text'],bold=True);text('AUTO',124,h-35,12,c['off_text'],bold=True)
    icon('ic_tune.xml',258,h-51,c['text']);icon('ic_settings.xml',312,h-51,c['text'])
    parts.append('</g></g>')

parts.append('</svg>');svg=''.join(parts)
(root/'interface-preview.svg').write_text(svg)
surface=cairo.ImageSurface(cairo.FORMAT_ARGB32,1120,1120)
context=cairo.Context(surface);handle=Rsvg.Handle.new_from_data(svg.encode())
area=Rsvg.Rectangle();area.x=0;area.y=0;area.width=1120;area.height=1120
handle.render_document(context,area);surface.write_to_png(str(root/'interface-preview.png'))
print(root/'interface-preview.png')
