var app = require('express')();
var http = require('http').Server(app);
var io = require('socket.io')(http);
var _ = require('lodash-node');

var users = [];

app.get('/', function (req, res){
  res.sendfile('index.html');
});

io.on('connection', function (socket) {

  socket.on('login', function (name) {
    // if this name is already registered,
    // send a failed login message back to the client
    if (_.contains(users, name)) {
      socket.broadcast.emit('login_failed');
      return; 
    }
    
    users.push({ 
      name: name,
      socket: socket.id
    });
  });

  socket.on('disconnect', function () {
    var index = _.findIndex(users, { socket: socket.id });
    if (index !== -1) {
      users.splice(index, 1);
    }

    console.log('user disconnected');
  });
});

http.listen(3000, function(){
  console.log('listening on *:3000');
});