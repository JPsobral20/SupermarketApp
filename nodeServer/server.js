'use strict';
const Koa = require('koa');
const logger = require('koa-logger');
const bodyParser = require('koa-bodyparser');
const router = require('./middleware/router');

const PORT = 8000;
const app = new Koa();

app.use(logger());
app.use(bodyParser({strict: false}));
app.use(router.routes());
app.use(router.allowedMethods());
app.listen(PORT, '0.0.0.0',() => console.log('Users server running on port %d', PORT));