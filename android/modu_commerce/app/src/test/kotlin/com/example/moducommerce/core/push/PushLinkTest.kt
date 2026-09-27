package com.example.moducommerce.core.push

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushLinkTest {

    @Test
    fun `only allow-listed paths make a link`() {
        for (p in listOf("/", "/coupons", "/products/1", "/promotions/42")) assertTrue(p, PushLink.isAllowed(p))
        for (p in listOf(null, "", "/orders", "/products/", "/products/1/reviews", "/products/abc", "//evil.com", "https://x/", "/coupons?x=1", "/products/1')")) {
            assertFalse(p.toString(), PushLink.isAllowed(p))
            assertNull(PushLink.from(p, "1"))
        }
    }

    @Test
    fun `campaign id is parsed when numeric and dropped otherwise`() {
        assertEquals(7L, PushLink.from("/coupons", "7")?.campaignId)
        assertNull(PushLink.from("/coupons", null)?.campaignId)
        assertNull(PushLink.from("/coupons", "abc")?.campaignId)
        assertEquals("/coupons", PushLink.from("/coupons", "abc")?.path)
    }

    @Test
    fun `builds the web url and the navigate script`() {
        val link = PushLink.from("/products/3", null)!!
        assertEquals("http://host:5174/products/3", link.webUrl("http://host:5174/"))
        assertEquals("http://host:5174/", PushLink.from("/", null)!!.webUrl("http://host:5174/"))
        assertEquals("window.ModuWeb && window.ModuWeb.navigate('/products/3')", link.navigateJs())
    }

    @Test
    fun `pending link keeps the newest and is taken once`() {
        val pending = PendingPushLink()
        val a = PushLink.from("/coupons", "1")!!
        val b = PushLink.from("/products/2", "2")!!
        pending.offer(a)
        pending.offer(b)
        assertFalse(pending.take(a))
        assertTrue(pending.take(b))
        assertNull(pending.takeAny())
        pending.offer(a)
        assertEquals(a, pending.takeAny())
        assertNull(pending.link.value)
    }
}
