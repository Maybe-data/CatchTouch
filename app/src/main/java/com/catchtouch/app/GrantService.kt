package com.catchtouch.app

class GrantService : IGrantService.Stub() {
    override fun destroy() = exit()
    override fun exit() { System.exit(0) }
    override fun pmGrant(packageName: String?, permission: String?): Boolean {
        return try {
            Runtime.getRuntime().exec(arrayOf("pm", "grant", packageName, permission)).waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }
}
