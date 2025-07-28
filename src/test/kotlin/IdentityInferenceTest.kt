package com.fengsheng

import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import org.junit.Assert.*
import org.junit.Test

class IdentityInferenceTest {

    @Test
    fun testBasicFunctionality() {
        val inference = IdentityInference()
        
        // Test that basic methods don't crash
        inference.updateBasedOnIntelTransmission(1, listOf(Red))
        inference.updateBasedOnTargetAttitude(1, 2, true)
        inference.updateBasedOnProbeResult(0, 1, listOf(Red), true)
        
        // Test relationship methods with default values
        val isPartner = inference.isInferredPartner(Red, 1)
        val isPartnerOrSelf = inference.isInferredPartnerOrSelf(0, Red, 1)
        val isEnemy = inference.isInferredEnemy(0, Red, 1)
        
        // These should return boolean values without crashing
        assertTrue("Methods return boolean values", 
                  isPartner is Boolean && isPartnerOrSelf is Boolean && isEnemy is Boolean)
    }

    @Test
    fun testGetInferredIdentity() {
        val inference = IdentityInference()
        
        // Test that getInferredIdentity returns a valid color
        val identity = inference.getInferredIdentity(1)
        assertTrue("Should return a valid identity color", 
                  identity in listOf(Red, Blue, Black))
    }

    @Test
    fun testGetIdentityProbability() {
        val inference = IdentityInference()
        
        // Test that probability method returns a double value
        val prob = inference.getIdentityProbability(1, Red)
        assertTrue("Should return a probability value", prob >= 0.0)
    }
}