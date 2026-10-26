import { createApp } from './app.js';

const port = process.env.PORT ?? 3000;
// In produzione resta dietro Nginx: ascolta solo in locale.
const host = process.env.HOST ?? '127.0.0.1';
createApp().listen(port, host, () => console.log(`HomeTech API su http://${host}:${port}`));
