package org.axonframework.examples.addressbook.utils {
import flash.events.EventDispatcher;

import flash.utils.setTimeout;

import mx.core.mx_internal;
import mx.rpc.AsyncToken;
import mx.rpc.events.ResultEvent;

use namespace mx_internal;

/**
 * Class copied from http://www.coenraets.org/apps/insyncparsley/srcview/index.html
 */
public class MockServiceUtil extends EventDispatcher {
    public function createToken(result:Object):AsyncToken {
        var token:AsyncToken = new AsyncToken(null);
        setTimeout(applyResult, Math.random()*500, token, result);
        return token;
    }

    private function applyResult(token:AsyncToken, result:Object):void {
        token.setResult(result);
        var event:ResultEvent = new ResultEvent(ResultEvent.RESULT, false, true, result, token);
        token.applyResult(event);
    }
}
}