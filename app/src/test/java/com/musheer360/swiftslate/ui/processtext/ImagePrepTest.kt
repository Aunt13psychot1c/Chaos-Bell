package com.musheer360.swiftslate.ui.processtext

import org.junit.Assert.assertEquals
import org.junit.Test

class ImagePrepTest {

    @Test
    fun big_phone_photos_are_sampled_down_but_never_below_the_limit() {
        assertEquals(2, ImagePrep.sampleSize(4000, 3000))   // 4000/2 = 2000 >= 1568, /4 = 1000 < 1568
        assertEquals(4, ImagePrep.sampleSize(8000, 6000))
        assertEquals(1, ImagePrep.sampleSize(1200, 800))
        assertEquals(2, ImagePrep.sampleSize(3000, 4000))   // tall photos too
        assertEquals(1, ImagePrep.sampleSize(3000, 2000))   // 3000/2 = 1500 < 1568
    }

    @Test
    fun final_size_fits_the_long_side_and_keeps_the_shape() {
        assertEquals(1568 to 1176, ImagePrep.fitWithin(2000, 1500))
        assertEquals(1176 to 1568, ImagePrep.fitWithin(1500, 2000))
        assertEquals(800 to 600, ImagePrep.fitWithin(800, 600))  // small pictures are never grown
        assertEquals(1568 to 1, ImagePrep.fitWithin(10000, 3))   // never collapses to zero
    }
}
