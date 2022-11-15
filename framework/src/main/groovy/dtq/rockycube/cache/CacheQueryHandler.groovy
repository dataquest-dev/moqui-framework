package dtq.rockycube.cache

import javax.cache.Cache

class CacheQueryHandler {
    protected Cache internalCache

    CacheQueryHandler(Cache cache){
        this.internalCache = cache
    }

}
