package com.catchtouch.app;

interface IGrantService {

    // Destroy method defined by Shizuku server
    void destroy() = 16777114;

    void exit() = 1;

    boolean pmGrant(String packageName, String permission) = 2;
}
