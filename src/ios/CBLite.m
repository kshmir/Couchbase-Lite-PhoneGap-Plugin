#import "CBLite.h"

#import "CouchbaseLite.h"
#import "CBLAuthenticator.h"
#import "CBLListener.h"
#import "CBLRegisterJSViewCompiler.h"

#import <Cordova/CDV.h>

// The name of the local database the app will create. This name is mostly arbitrary, but must not
// be changed after deploying your app, or users will lose their data!
// (Note that database names cannot contain uppercase letters.)
#define kDatabaseName @"contacts"

// The remote database URL to sync with. This is preconfigured with a sample database we maintain.
// In your own apps you will of course want to set this to a database you run, on your own Sync
// Gateway instance.
#define kServerDbURL @"http://45.79.199.113:4988/contacts"


@implementation CBLite {
  __strong NSString * username;
  __strong NSString * password;
  NSURL* remoteSyncURL;
  CBLReplication* _pull;
  CBLReplication* _push;
  NSError* _syncError;
  CBLDatabase * database;
}

@synthesize liteURL;

- (id) initWithWebView:(UIWebView*)theWebView
{
    self = [super initWithWebView:theWebView];
    if (self) {
        // todo check domain whitelist to give devs a helpful error message
        [self launchCouchbaseLite];
    }
    return self;
}

- (void)getURL:(CDVInvokedUrlCommand*)urlCommand
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[self.liteURL absoluteString]];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:urlCommand.callbackId];
}

- (void)startSync:(CDVInvokedUrlCommand*)urlCommand
{
    username = [urlCommand argumentAtIndex:0];
    password = [urlCommand argumentAtIndex:1];
    NSString * syncUrl = kServerDbURL;
    if ([[urlCommand arguments] count] > 2) {
      syncUrl = [urlCommand argumentAtIndex:2];
    }

    NSURL* serverDbURL = [NSURL URLWithString: syncUrl];
    _pull = [database createPullReplication: serverDbURL];
    _push = [database createPushReplication: serverDbURL];
    id<CBLAuthenticator> auth = (id<CBLAuthenticator>) [CBLAuthenticator basicAuthenticatorWithName:username password:password];
    _pull.authenticator = auth;
    _push.authenticator = auth;
    _pull.continuous = _push.continuous = YES;
  
    [_push start];
    [_pull start];
  
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"OK"];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:urlCommand.callbackId];
}

- (void)launchCouchbaseLite
{
    NSLog(@"Launching Couchbase Lite...");
    NSError* error;
    database = [[CBLManager sharedInstance] databaseNamed: kDatabaseName error: &error];
  
    CBLRegisterJSViewCompiler();
#if 1
    // Couchbase Lite 1.0's CBLRegisterJSViewCompiler function doesn't register the filter compiler
    if ([CBLDatabase filterCompiler] == nil) {
        Class cblJSFilterCompiler = NSClassFromString(@"CBLJSFilterCompiler");
        [CBLDatabase setFilterCompiler: [[cblJSFilterCompiler alloc] init]];
    }
#endif
    CBLListener * listener =[[CBLListener alloc] initWithManager:[CBLManager sharedInstance] port:5984];
    self.liteURL = listener.URL;
    [listener start:&error];
    NSLog(@"Couchbase Lite url = %@", self.liteURL);
}

@end

