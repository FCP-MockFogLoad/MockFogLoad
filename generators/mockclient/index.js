/** HTTP Server */

const bodyParser = require("body-parser");
const express = require("express");
const app = express();
const port = 3000;

app.use(express.json());
app.use(bodyParser.text());

app.post("/", (req, res) => {
  console.log(req.body);
  res.send();
});

app.listen(port, () => console.log(`Mock client listening on port ${port}!`));

/** UDP Server */

var udp = require("dgram");
var server = udp.createSocket("udp4");

server.on("error", function(error) {
  console.log("Error: " + error);
  server.close();
});

server.on("message", function(msg, info) {
  console.log("Data received from client : " + msg.toString());
  console.log(
    "Received %d bytes from %s:%d\n",
    msg.length,
    info.address,
    info.port
  );

  server.send(msg, info.port, "localhost", function(error) {
    if (error) {
      client.close();
      console.log("Closing UDP server because of error: " + error);
    }
  });
});

server.on("listening", function() {
  var address = server.address();
  var port = address.port;
  var family = address.family;
  var ipaddr = address.address;
  console.log("UDP Server is listening on port " + port);
  console.log("UDP Server IP: " + ipaddr);
  console.log("UDP Server is: " + family);
});

server.on("close", function() {
  console.log("closing socket...");
});

server.bind(2222);
