/*
 * Copyright (c) 2021, Sean Maloney <https://github.com/SMaloney2017>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.pvpprofitcalc;

import com.google.inject.Inject;
import net.runelite.api.Client;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import static net.runelite.client.RuneLite.RUNELITE_DIR;

public class PvpProfitCalcSession {
    private final Client client;
    private final String path;
    private Collection < PvpProfitCalcRecord > entries = new ArrayList < > ();

    @Inject
    public PvpProfitCalcSession(Client client) {
        this.client = client;
        this.path = RUNELITE_DIR + "/pvp-profit-calc/" + client.getUsername() + ".txt";
    }

    public void addToSession(PvpProfitCalcRecord record) {
        entries.add(record);
        rebuildSessionFile(this.entries);
    }

    void removeFromSession(PvpProfitCalcRecord record) {
        entries.removeIf(e -> e.matches(record.getTitle(), record.getType()));
        rebuildSessionFile(this.entries);
    }

    void removeAllFromSession() {
        entries.clear();
        File file = new File(path);
        file.delete();
    }

    public void rebuildSessionFile(Collection < PvpProfitCalcRecord > entries) {
        try
        {
            File sessionFile = new File(path);

            if (!sessionFile.createNewFile())
            {
                sessionFile.delete();
                sessionFile.createNewFile();
            }

            try (
                    FileOutputStream f = new FileOutputStream(sessionFile);
                    ObjectOutputStream b = new ObjectOutputStream(f);
                    )
            {
                for(PvpProfitCalcRecord r : entries)
                {
                    b.writeObject(r);
                }
            }
            catch (IOException i)
            {
                i.printStackTrace();
            }
        }
        catch (IOException  e)
        {
            e.printStackTrace();
        }
    }

    ArrayList < PvpProfitCalcRecord > getSessionFileEntries() {
        ArrayList < PvpProfitCalcRecord > entries = new ArrayList < > ();
        File sessionFile = new File(path);

        if (!sessionFileExists())
        {
            return entries;
        }

        try (
                FileInputStream f = new FileInputStream(sessionFile);
                ObjectInputStream b = new ObjectInputStream(f);
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

    boolean sessionFileExists() {
        return new File(path).exists();
    }

    void createNewUserFile() {
        File file = new File(path);
        try{
            file.createNewFile();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }
}