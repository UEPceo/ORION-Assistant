package com.orion.voicelab.creative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaProviderRouterTest {
 private data class Fake(val kind:MediaProviderKind,val status:MediaCapabilityStatus)
 private val local=Fake(MediaProviderKind.LOCAL,MediaCapabilityStatus.AVAILABLE)
 private val cloud=Fake(MediaProviderKind.REMOTE,MediaCapabilityStatus.AVAILABLE)
 @Test fun localAlwaysWins(){assertEquals(local,MediaProviderRouter.select(listOf(cloud,local),{it.kind},{it.status},MediaRoutingPolicy(true,true)))}
 @Test fun cloudNeedsExplicitConsent(){val p=listOf(cloud);val policy=MediaRoutingPolicy(networkAvailable=true);assertNull(MediaProviderRouter.select(p,{it.kind},{it.status},policy));assertEquals(MediaCapabilityStatus.REQUIRES_USER_CONSENT,MediaProviderRouter.status(p,{it.kind},{it.status},policy))}
 @Test fun cloudNeedsNetwork(){val p=listOf(cloud);val policy=MediaRoutingPolicy(true,false);assertNull(MediaProviderRouter.select(p,{it.kind},{it.status},policy));assertEquals(MediaCapabilityStatus.REQUIRES_NETWORK,MediaProviderRouter.status(p,{it.kind},{it.status},policy))}
 @Test fun cloudRunsAfterConsentAndNetwork(){val policy=MediaRoutingPolicy(true,true);assertEquals(cloud,MediaProviderRouter.select(listOf(cloud),{it.kind},{it.status},policy))}
 @Test fun missingProviderIsExplicit(){assertEquals(MediaCapabilityStatus.REQUIRES_CONFIGURATION,MediaProviderRouter.status<Fake>(emptyList(),{it.kind},{it.status},MediaRoutingPolicy()))}
}
