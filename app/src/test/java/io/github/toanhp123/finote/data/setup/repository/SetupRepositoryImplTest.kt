package io.github.toanhp123.finote.data.setup.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupRepositoryImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context
            .getSharedPreferences("app_setup", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `setup is not done by default`() {
        assertFalse(SetupRepositoryImpl(context).isSetupDone())
    }

    @Test
    fun `a new instance reads the stored flag`() {
        SetupRepositoryImpl(context).markSetupDone()

        assertTrue(SetupRepositoryImpl(context).isSetupDone())
    }
}
