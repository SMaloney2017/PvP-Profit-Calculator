package com.pvpprofitcalc;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PvpProfitCalcPluginTest {
	public static void main(String[] args) throws Exception {
		ExternalPluginManager.loadBuiltin(PvpProfitCalcPlugin.class);
		RuneLite.main(args);
	}
}