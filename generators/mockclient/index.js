/** HTTP Server */

const bodyParser = require("body-parser");
const express = require("express");
const app = express();
const port = 3000;

app.use(express.json());
app.use(bodyParser.text());

app.post("/", (req, res) => {
  console.log("[HTTP] " + req.body);
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
  console.log("[UDP] " + msg.toString());

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

/** COAP Server */

var coap = require("coap"),
  coapServer = coap.createServer(),
  coapPort = 5683;

coapServer.on("request", function(req, res) {
  console.log(`[CoAP] ${req.payload}`);
  res.end();
});

// the default CoAP port is 5683
coapServer.listen(coapPort, function() {
  console.log(`COAP server is listening on port ${coapPort}`);
});
