import { createApp } from './app.js';

const port = process.env.PORT ?? 3000;
createApp().listen(port, () => console.log(`HomeTech API su http://localhost:${port}`));
