package com.flx_apps.digitaldetox.util

/**
 * The apps the group templates know by name, by what people lose time in. The OS category
 * (`ApplicationInfo.category`, set by the store for most apps) fills in what the lists miss, so
 * a game that is not named here is still a game. Pure Kotlin, like [knownCategoryOf], whose
 * coarser lists these build on.
 *
 * A messenger is the one thing a feed template must not catch: a chat is what people keep when
 * they cut the feeds, and the social lists lump the two together.
 */
object KnownApps {
    /** Apps that are nothing but a short-video feed. */
    val shortVideo: Set<String> = setOf(
        "com.zhiliaoapp.musically", "com.zhiliaoapp.musically.go", "com.ss.android.ugc.trill",
        "com.ss.android.ugc.trill.go", "com.ss.android.ugc.aweme", "com.ss.android.ugc.aweme.lite",
        "com.kwai.video", "com.kwai.bulldog", "video.like", "video.like.lite", "co.triller.droid",
        "in.mohalla.video", "com.eterno.shortvideos", "com.bd.nproject", "com.ss.android.ugc.boom",
        "com.ss.android.ugc.boomlite",
    )

    /** Social networks and their clients: whatever opens on an endless feed. */
    val feeds: Set<String> = setOf(
        "com.instagram.android", "com.instagram.barcelona", "com.facebook.katana", "com.facebook.lite",
        "com.twitter.android", "com.twitter.android.lite", "com.reddit.frontpage", "com.snapchat.android",
        "com.pinterest", "com.tumblr", "xyz.blueskyweb.app", "com.linkedin.android", "com.vkontakte.android",
        "ru.ok.android", "com.ninegag.android.app", "com.imgur.mobile", "com.quora.android", "com.sina.weibo",
        "com.xingin.xhs", "com.zhihu.android", "com.nextdoor", "com.jodelapp.jodelandroidv3", "com.bereal.ft",
        "com.truthsocial.android.app",
        // Mastodon, Lemmy and Reddit clients
        "org.joinmastodon.android", "com.keylesspalace.tusky", "com.jerboa",
        "ml.docilealligator.infinityforreddit", "me.ccrama.redditslide", "com.laurencedawson.reddit_sync",
        "com.rubenmayayo.reddit",
    )

    /** Chat apps, kept out of the feed templates and in color on a gray phone. */
    val messengers: Set<String> = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.thoughtcrime.securesms", "org.telegram.messenger",
        "org.telegram.messenger.web", "org.telegram.messenger.beta", "ch.threema.app", "ch.threema.app.libre",
        "ch.threema.app.work", "com.facebook.orca", "com.facebook.mlite", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.android.mms", "com.viber.voip", "jp.naver.line.android",
        "com.kakao.talk", "com.tencent.mm", "com.tencent.mobileqq", "com.discord", "com.Slack",
        "com.microsoft.teams", "com.skype.raider", "com.imo.android.imoim", "kik.android", "im.vector.app",
        "io.element.android.x", "com.wire", "network.loki.messenger", "chat.simplex.app", "com.beeper.android",
        "com.google.android.apps.dynamite", "com.google.android.apps.googlevoice", "com.textra",
        "org.briarproject.briar.android", "com.enflick.android.TextNow", "com.icq.mobile.client",
    )

    /** Video and streaming services, the ones that play the next episode on their own. */
    val streaming: Set<String> = setOf(
        "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus", "com.wbd.stream",
        "com.hbo.hbonow", "tv.twitch.android.app", "com.crunchyroll.crunchyroid", "com.hulu.plus",
        "com.peacocktv.peacockandroid", "com.cbs.app", "com.google.android.youtube",
        "com.google.android.apps.youtube.unplugged", "tv.pluto.android", "com.tubitv", "com.kick.mobile",
        "com.dailymotion.dailymotion", "com.vimeo.android.videoapp", "com.rumble.battles", "tv.danmaku.bili",
        "de.zdf.android.mediathek", "de.prosiebensat1digital.seventv", "com.plexapp.android", "com.sling",
        "com.mxtech.videoplayer.ad",
    )

    /** News apps and aggregators. */
    val news: Set<String> = setOf(
        "com.google.android.apps.magazines", "flipboard.app", "com.nytimes.android", "bbc.mobile.news.ww",
        "bbc.mobile.news.uk", "com.guardian", "com.cnn.mobile.android.phone", "com.foxnews.android",
        "com.washingtonpost.android", "com.thomsonreuters.reuters", "com.wsj.reader_sp", "com.ft.news",
        "com.economist.lamarr", "com.dailymail.online", "com.bloomberg.android.plus", "mnn.Android",
        "org.npr.android.news", "jp.gocro.smartnews.android", "com.particlenews.newsbreak",
        "com.microsoft.amp.apps.bingnews", "com.yahoo.mobile.client.android.yahoo", "com.opera.app.news",
        "com.eterno", "com.nis.app",
        // German
        "de.spiegel.android.app.spon", "com.netbiscuits.bild.android", "de.tagesschau", "de.zeit.online",
        "net.faz.FAZ", "de.welt", "de.lineas.ntv.android",
    )

    /** Shops and marketplaces, on top of the shopping apps [knownCategoryOf] knows. */
    val shopping: Set<String> = setOf(
        "com.einnovation.temu", "com.zzkko", "de.zalando.mobile", "fr.vinted", "de.idealo.android", "com.depop",
        "com.flipkart.android", "com.myntra.android", "com.meesho.supply", "com.target.ui", "com.bestbuy.android",
        "com.shopify.arrive", "com.myklarnamobile", "com.hm.goe", "com.inditex.zara", "com.nike.omega",
        "com.asos.app", "com.poshmark.app", "com.groupon", "com.lazada.android", "com.tokopedia.tkpd",
        "de.cellular.ottohybrid",
    )

    /** The games big enough to be worth naming; the OS category catches the rest. */
    val games: Set<String> = setOf(
        "com.king.candycrushsaga", "com.king.candycrushsodasaga", "com.supercell.clashofclans",
        "com.supercell.clashroyale", "com.supercell.brawlstars", "com.supercell.hayday", "com.roblox.client",
        "com.mojang.minecraftpe", "com.tencent.ig", "com.pubg.imobile", "com.dts.freefireth", "com.dts.freefiremax",
        "com.miHoYo.GenshinImpact", "com.HoYoverse.hkrpgoversea", "com.nianticlabs.pokemongo", "com.kiloo.subwaysurf",
        "com.innersloth.spacemafia", "com.moonactive.coinmaster", "com.dreamgames.royalmatch",
        "com.scopely.monopolygo", "com.activision.callofduty.shooter", "com.miniclip.eightballpool",
        "com.playrix.homescapes", "com.playrix.gardenscapes", "com.playrix.fishdomdd.gplay", "com.playrix.township",
        "com.ea.gp.fifamobile", "com.mobile.legends", "com.epicgames.fortnite", "com.ludo.king",
        "com.gameloft.android.ANMP.GloftA9HM", "com.outfit7.mytalkingtomfree", "com.rovio.baba",
        "com.riotgames.league.wildrift", "com.riotgames.league.teamfighttactics", "com.imangi.templerun2",
        "com.fingersoft.hillclimb", "com.blizzard.wtcg.hearthstone", "com.blizzard.diablo.immortal",
        "com.levelinfinite.sgameGlobal",
    )

    /** Sports betting; the gambling website collection covers the rest. */
    val betting: Set<String> = setOf(
        "com.bet365Wrapper.Bet365_Application", "com.draftkings.sportsbook", "com.fanduel.sportsbook",
        "com.bwinlabs.betdroid",
    )

    /** What a gray phone still needs in color, besides what the phone itself resolves. */
    val clocksAndCalendars: Set<String> = setOf(
        "com.google.android.deskclock", "com.android.deskclock", "com.sec.android.app.clockpackage",
        "com.google.android.calendar", "com.samsung.android.calendar",
    )

    fun isShortVideo(packageName: String): Boolean = packageName in shortVideo

    /** A social feed, a short-video feed included; never a messenger, whatever the store says. */
    fun isFeed(packageName: String, osCategory: Int): Boolean =
        packageName in feeds || packageName in shortVideo ||
                packageName !in messengers && (
                        knownCategoryOf(packageName) == KnownAppCategory.SOCIAL ||
                                osCategory == DistractingAppsHeuristic.OS_CATEGORY_SOCIAL
                        )

    fun isStreaming(packageName: String, osCategory: Int): Boolean =
        packageName in streaming || knownCategoryOf(packageName) == KnownAppCategory.VIDEO ||
                osCategory == DistractingAppsHeuristic.OS_CATEGORY_VIDEO

    fun isNews(packageName: String, osCategory: Int): Boolean =
        packageName in news || osCategory == DistractingAppsHeuristic.OS_CATEGORY_NEWS

    fun isShop(packageName: String): Boolean =
        packageName in shopping || knownCategoryOf(packageName) == KnownAppCategory.SHOPPING

    fun isGame(packageName: String, osCategory: Int): Boolean =
        packageName in games || osCategory == DistractingAppsHeuristic.OS_CATEGORY_GAME

    fun isBetting(packageName: String): Boolean = packageName in betting
}
