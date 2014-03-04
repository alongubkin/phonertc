var phonertc = { 
	call: function (options) {
		cordova.exec(
			function (data) { 
				if (data.type === '__answered' && options.answerCallback) {
					options.answerCallback();
				} else if (data.type === '__disconnected' && options.disconnectCallback) {
					options.disconnectCallback();
				} else {
					options.sendMessageCallback(data);
				}
			},
			null,
			'PhoneRTCPlugin',
			'call',
			[options.isInitator, options.turn.host, options.turn.username, options.turn.password]);
	},
	receiveMessage: function (data) { 
		cordova.exec(
			null, 
			null,
			'PhoneRTCPlugin',
			'receiveMessage',
			[JSON.stringify(data)]);
	},
	disconnect: function () {
		cordova.exec(
			null, 
			null,
			'PhoneRTCPlugin',
			'disconnect',
			[]);
	}
};
