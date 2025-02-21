var exec = require('cordova/exec');

exports.initLinphoneCore = function(success, fail) {
    exec(success, fail, "LinphonePlugin", "initLinphoneCore", []);
};

exports.registerSIP = function(username,displayName,domain,password,transport,success, fail) {
    exec(success, fail, "LinphonePlugin", "registerSIP", [username,displayName,domain,password,transport]);
};

exports.unregisterSIP = function(username,displayName,domain,password,transport,success, fail) {
    exec(success, fail, "LinphonePlugin", "unregisterSIP", [username,displayName,domain,password,transport]);
};

exports.acceptCall = function(success, fail) {
    exec(success, fail, "LinphonePlugin", "acceptCall", []);
};

exports.makeCall = function(username,domain,displayName, success, fail) {
    exec(success, fail, "LinphonePlugin", "makeCall", [username,domain,displayName]);
};

exports.listenForDTMF = function(success, fail) {
    exec(success, fail, "LinphonePlugin", "listenForDTMF", []);
};

exports.stop = function(success, fail) {
    exec(success, fail, "LinphonePlugin", "listenForDTMF", []);
};