"""Development only: no credential handling or call processing."""
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import shutil
root=Path(__file__).resolve().parents[1]
out=root/'build'/'preview'
shutil.copytree(root/'app/src/main/assets',out,dirs_exist_ok=True)
shutil.copytree(root/'caller',out/'call',dirs_exist_ok=True)
class Preview(SimpleHTTPRequestHandler):
    def __init__(self,*a,**k):super().__init__(*a,directory=str(out),**k)
ThreadingHTTPServer(('0.0.0.0',8080),Preview).serve_forever()
