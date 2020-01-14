
const bodyParser = require('body-parser');
const express = require("express");
const app = express();
const port = 3000;

app.use(express.json());
app.use(bodyParser.text());

app.post('/', (req, res) => {
    console.log(req.body);
    res.send();
});

app.listen(port, () => console.log(`Mock client listening on port ${port}!`));