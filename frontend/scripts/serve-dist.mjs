import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';

const root = join(process.cwd(), 'dist');
const port = Number(process.env.PORT || 5173);

const contentTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.webp': 'image/webp'
};

createServer((request, response) => {
  const pathname = decodeURIComponent(new URL(request.url || '/', `http://localhost:${port}`).pathname);
  const normalized = normalize(pathname).replace(/^(\.\.[/\\])+/, '');
  let filePath = join(root, normalized);
  if (!existsSync(filePath) || statSync(filePath).isDirectory()) {
    filePath = join(root, 'index.html');
  }
  response.setHeader('Content-Type', contentTypes[extname(filePath)] || 'application/octet-stream');
  createReadStream(filePath)
    .on('error', () => {
      response.statusCode = 404;
      response.end('Not found');
    })
    .pipe(response);
}).listen(port, '127.0.0.1', () => {
  console.log(`PPT Agent frontend preview: http://localhost:${port}`);
});
