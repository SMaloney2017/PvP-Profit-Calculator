package com.pvpprofitcalc;

import com.google.inject.Inject;
import net.runelite.api.Client;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import static net.runelite.client.RuneLite.RUNELITE_DIR;

public class PvpProfitCalcSession
{
	private final Client client;
	private final String path;
	private Collection<PvpProfitCalcRecord> entries = new ArrayList<>();

	@Inject
	public PvpProfitCalcSession(Client client)
	{
		this.client = client;
		this.path = RUNELITE_DIR + "/pvp-profit-calc/" + client.getAccountHash() + ".txt";
	}

	public void addToSession(PvpProfitCalcRecord record)
	{
		entries.add(record);
		rebuildSessionFile(this.entries);
	}

	void removeFromSession(PvpProfitCalcRecord record)
	{
		entries.removeIf(e -> e.matches(record.getTitle(), record.getType()));
		rebuildSessionFile(this.entries);
	}

	void removeAllFromSession()
	{
		entries.clear();
		File file = new File(path);
		file.delete();
	}

	public void rebuildSessionFile(Collection<PvpProfitCalcRecord> entries)
	{
		try
		{
			File sessionFile = new File(path);

			if (!sessionFile.createNewFile())
			{
				sessionFile.delete();
				sessionFile.createNewFile();
			}

			try (
				FileOutputStream f = new FileOutputStream(sessionFile); ObjectOutputStream b = new ObjectOutputStream(f);
			)
			{
				for (PvpProfitCalcRecord r : entries)
				{
					b.writeObject(r);
				}
			}
			catch (IOException i)
			{
				i.printStackTrace();
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	ArrayList<PvpProfitCalcRecord> getSessionFileEntries()
	{
		ArrayList<PvpProfitCalcRecord> entries = new ArrayList<>();
		File sessionFile = new File(path);

		if (!sessionFileExists() || sessionFile.length() == 0)
		{
			return entries;
		}

		try (
			FileInputStream f = new FileInputStream(sessionFile); ObjectInputStream b = new ObjectInputStream(f);
		)
		{
			try
			{
				while (true)
				{
					PvpProfitCalcRecord record = (PvpProfitCalcRecord) b.readObject();
					entries.add(record);
				}
			}
			catch (Exception e)
			{
				/* Exit */
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		return entries;
	}

	boolean sessionFileExists()
	{
		return new File(path).exists();
	}

	void createNewUserFile()
	{
		File file = new File(path);
		try
		{
			file.getParentFile().mkdirs();
			file.createNewFile();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}