package io.github.toanhp123.finote.architecture

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootClassNameTest {
    @Test
    fun `generated R and BuildConfig are allowed`() {
        assertFalse("$ROOT.R".isRootClassName())
        assertFalse("$ROOT.R.string.app_name".isRootClassName())
        assertFalse("$ROOT.BuildConfig".isRootClassName())
        assertFalse("$ROOT.BuildConfig.DEBUG".isRootClassName())
    }

    @Test
    fun `app entry points and look-alikes are not allowed`() {
        assertTrue("$ROOT.MainActivity".isRootClassName())
        assertTrue("$ROOT.FinoteApplication".isRootClassName())
        assertTrue("$ROOT.Resources".isRootClassName())
        assertTrue("$ROOT.R2".isRootClassName())
    }

    @Test
    fun `layer packages and foreign names are not root classes`() {
        assertFalse("$DATA.Foo".isRootClassName())
        assertFalse("android.R".isRootClassName())
    }
}
