package com.strikernz.cgmistake;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class CGMistakeAnnouncerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(CGMistakeAnnouncerPlugin.class);
		RuneLite.main(args);
	}
}
