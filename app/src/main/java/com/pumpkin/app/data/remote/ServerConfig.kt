package com.pumpkin.app.data.remote

/**
 * Base URLs for the self-hosted chat backend (server/). 10.0.2.2 is the
 * Android emulator's special alias for the host machine's localhost — this
 * lets you test against `npm start` running on your dev machine with zero
 * deployment. Swap both to your Render/Railway https:// URL once deployed
 * (see server/README.md), and drop the 10.0.2.2/localhost exception in
 * res/xml/network_security_config.xml at that point too.
 */
object ServerConfig {
    const val BASE_URL = "http://10.0.2.2:4000/"
    const val SOCKET_URL = "http://10.0.2.2:4000"
}
