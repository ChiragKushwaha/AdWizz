package rocks.chirag.adwizz.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.test.*
import org.junit.Assert.*
import org.junit.Test

class WizardArtTest {
    @Test fun framesAreFullSizeAndUseOnlyPaletteColours() {
        listOf(WizardArt.IDLE, WizardArt.CASTING).forEach { frame ->
            assertEquals(WizardArt.HEIGHT, frame.size)
            frame.forEach { row ->
                assertEquals(WizardArt.WIDTH, row.length)
                row.forEach { ch -> assertTrue("unknown colour '$ch'", ch == '.' || ch in WizardArt.palette) }
            }
        }
    }

    @Test fun wandTipIsDrawnInTheCastingFrame() {
        val tip = WizardArt.CASTING[WizardArt.WAND_Y][WizardArt.WAND_X]
        assertEquals('S', tip)
        assertNotEquals(WizardArt.IDLE.joinToString(), WizardArt.CASTING.joinToString())
    }

    @Test fun wizardHasHatHairCloakEyesAndBroom() {
        val all = WizardArt.CASTING.joinToString("")
        listOf('H', 'N', 'C', 'K', 'Y', 'F', 'E').forEach { assertTrue("missing '$it'", it in all) }
    }
}

class DesignArtTest {
    @Test fun spritesAreRectangularAndUseTheirPalettes() {
        listOf(DesignArt.BAT to DesignArt.BAT_PALETTE, DesignArt.HEART to DesignArt.HEART_PALETTE, DesignArt.OWL to DesignArt.OWL_PALETTE).forEach { (rows, pal) ->
            assertTrue(rows.isNotEmpty())
            rows.forEach { r ->
                assertEquals(rows[0].length, r.length)
                r.forEach { ch -> assertTrue("'$ch'", ch == '.' || ch in pal) }
            }
        }
    }

    /** Matches the bat in design/image.png: wide wings, two ears, yellow eyes, little tail. */
    @Test fun batLooksLikeTheDesign() {
        assertEquals(listOf("OOO.O.O.OOO", ".OOOYOYOOO.", "..O.OOO.O..", ".....O....."), DesignArt.BAT.toList())
    }

    /** Heart is symmetric apart from its shine, outlined, with a single point at the bottom. */
    @Test fun heartShape() {
        val h = DesignArt.HEART
        assertEquals(".....O.....", h.last())
        h.forEach { r -> assertEquals(r.replace('W', 'R'), r.replace('W', 'R').reversed()) }
        assertTrue(h.joinToString("").contains('W'))
    }
}

class OwlArtTest {
    @Test fun owlHasTwoYellowEyesAndOutline() {
        val all = DesignArt.OWL.joinToString("")
        assertEquals(2, all.count { it == 'Y' })
        assertTrue(all.count { it == 'O' } > 30)
        assertTrue(all.count { it == 'W' } > 40)  // a white snowy owl
    }
}
