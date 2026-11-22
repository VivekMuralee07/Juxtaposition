const app = require('./src/app');
const port = process.env.PORT || 3000;
app.set('trust proxy', 1); // Render sits behind a proxy
app.listen(port, '0.0.0.0', () => console.log(`Juxtaposition API listening on ${port}`));
