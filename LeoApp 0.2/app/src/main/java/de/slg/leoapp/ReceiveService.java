package de.slg.leoapp;

import android.app.Service;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;

import de.slg.messenger.Assoziation;
import de.slg.messenger.Chat;
import de.slg.messenger.Message;
import de.slg.messenger.Verschluesseln;
import de.slg.schwarzes_brett.SQLiteConnector;

public class ReceiveService extends Service {
    private static long interval;
    boolean receiveMessages, receiveNews;
    private boolean running;

    private static long getInterval(int selection) {
        switch (selection) {
            case 0:
                return 5000;
            case 1:
                return 10000;
            case 3:
                return 30000;
            case 4:
                return 60000;
            case 5:
                return 120000;
            case 6:
                return 300000;
            default:
                return 15000;
        }
    }

    public static void setInterval(int selection) {
        interval = getInterval(selection);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Utils.context = getApplicationContext();
        Utils.registerReceiveService(this);

        running = true;
        receiveMessages = false;
        receiveNews = false;
        interval = getInterval(Utils.getPreferences().getInt("pref_key_refresh", 2));

        new MessengerThread().start();
        new NewsThread().start();
        new MessengerSocket().start();

        Log.i("ReceiveService", "Service (re)started!");
        return START_STICKY;
    }

    @Override
    @Nullable
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        Utils.registerReceiveService(null);
        Log.i("ReceiveService", "Service stopped!");
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.e("ReceiveService", "TASK REMOVED!");
        Utils.getMDB().close();
        Utils.invalidateMDB();
        super.onTaskRemoved(rootIntent);
    }

    private class MessengerThread extends Thread {
        @Override
        public void run() {
            Looper.prepare();
            while (running) {
                try {
                    if (Utils.checkNetwork())
                        new SendTask().execute();
                    for (int i = 0; i < interval && running && !receiveMessages; i++)
                        sleep(1);
                    receiveMessages = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private class NewsThread extends Thread {
        @Override
        public void run() {
            while (running) {
                try {
                    new EmpfangeDaten().execute();
                    for (int i = 0; i < 60000 && running && !receiveNews; i++)
                        sleep(1);
                    receiveNews = false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    private class SendTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (Utils.checkNetwork()) {
                Message[] array = Utils.getMDB().getUnsendMessages();
                for (Message m : array) {
                    try {
                        BufferedReader reader =
                                new BufferedReader(
                                        new InputStreamReader(
                                                new URL(generateURL(m.mtext, m.cid))
                                                        .openConnection()
                                                        .getInputStream(), "UTF-8"));
                        while (reader.readLine() != null)
                            ;
                        reader.close();
                        Utils.getMDB().removeUnsendMessage(m.mid);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            return null;
        }

        private String generateURL(String message, int cid) {
            return Utils.BASE_URL + "messenger/addMessage.php?key=5453&userid=" + Utils.getUserID() + "&message=" + message.replace(" ", "%20").replace(System.getProperty("line.separator"), "%0A") + "&chatid=" + cid;
        }
    }

    private class EmpfangeDaten extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            if (Utils.checkNetwork()) {
                try {
                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            new URL(Utils.BASE_URL + "schwarzesBrett/meldungen.php")
                                                    .openConnection()
                                                    .getInputStream(), "UTF-8"));
                    StringBuilder builder = new StringBuilder();
                    String        line;
                    while ((line = reader.readLine()) != null)
                        builder.append(line)
                                .append(System.getProperty("line.separator"));
                    reader.close();
                    SQLiteConnector db  = new SQLiteConnector(getApplicationContext());
                    SQLiteDatabase  dbh = db.getWritableDatabase();
                    dbh.execSQL("DELETE FROM SQLITE_SEQUENCE WHERE NAME = '" + SQLiteConnector.TABLE_EINTRAEGE + "'");
                    dbh.delete(SQLiteConnector.TABLE_EINTRAEGE, null, null);
                    String[] result = builder.toString().split("_next_");
                    for (String s : result) {
                        String[] res = s.split(";");
                        if (res.length == 8) {
                            dbh.insert(SQLiteConnector.TABLE_EINTRAEGE, null, db.getContentValues(
                                    res[0],
                                    res[1],
                                    res[2],
                                    Long.parseLong(res[3] + "000"),
                                    Long.parseLong(res[4] + "000"),
                                    Integer.parseInt(res[5]),
                                    Integer.parseInt(res[6]),
                                    res[7]
                            ));
                        }
                    }
                    dbh.close();
                    db.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (Utils.getSchwarzesBrettActivity() != null)
                Utils.getSchwarzesBrettActivity().refreshUI();
            Log.i("ReceiveService", "received News");
        }
    }

    private class MessengerSocket extends Thread {
        @Override
        public void run() {
            try {
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        new URL("http://192.168.0.107:8080/messenger/getMessages?uid=" + Utils.getUserID())
                                                .openConnection()
                                                .getInputStream(), "UTF-8"));

                StringBuilder builder = new StringBuilder();
                for (String line = reader.readLine(); running; line = reader.readLine()) {
                    builder.append(line)
                            .append(System.getProperty("line.separator"));
                    if (line.endsWith("_ next _")) {
                        for (String s : builder.toString().split("_ next _")) {
                            String[] parts = s.substring(1).split("_ ; _");

                            if (s.startsWith("m") && parts.length == 6) {
                                int    mid   = Integer.parseInt(parts[0]);
                                String mtext = Verschluesseln.decrypt(parts[1], Verschluesseln.decryptKey(parts[2])).replace("_  ;  _", "_ ; _").replace("_  next  _", "_ next _");
                                long   mdate = Long.parseLong(parts[3] + "000");
                                int    cid   = Integer.parseInt(parts[4]);
                                int    uid   = Integer.parseInt(parts[5]);

                                Utils.getMDB().insertMessage(new Message(mid, mtext, mdate, cid, uid));
                            } else if (s.startsWith("c") && parts.length == 3) {
                                int           cid   = Integer.parseInt(parts[0]);
                                String        cname = parts[1].replace("_  ;  _", "_ ; _").replace("_  next  _", "_ next _");
                                Chat.ChatType ctype = Chat.ChatType.valueOf(parts[2].toUpperCase());

                                Utils.getMDB().insertChat(new Chat(cid, cname, ctype));
                            } else if (s.startsWith("u") && parts.length == 5) {
                                int    uid          = Integer.parseInt(parts[0]);
                                String uname        = parts[1].replace("_  ;  _", "_ ; _").replace("_  next  _", "_ next _");
                                String ustufe       = parts[2];
                                int    upermission  = Integer.parseInt(parts[3]);
                                String udefaultname = parts[4];

                                Utils.getMDB().insertUser(new User(uid, uname, ustufe, upermission, udefaultname));
                            } else if (s.startsWith("a")) {
                                assoziationen();
                            }
                        }

                        builder = new StringBuilder();

                        if (Utils.getMessengerActivity() != null)
                            Utils.getMessengerActivity().notifyUpdate();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void assoziationen() {
            try {
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        new URL(Utils.BASE_URL + "messenger/getAssoziationen.php?key=5453&userid=" + Utils.getUserID())
                                                .openConnection()
                                                .getInputStream(), "UTF-8"));
                StringBuilder builder = new StringBuilder();
                String        l;
                while ((l = reader.readLine()) != null)
                    builder.append(l);
                reader.close();
                String            erg    = builder.toString();
                String[]          result = erg.split(";");
                List<Assoziation> list   = new List<>();
                for (String s : result) {
                    String[] current = s.split(",");
                    if (current.length == 2) {
                        list.append(new Assoziation(Integer.parseInt(current[0]), Integer.parseInt(current[1])));
                    }
                }
                Utils.getMDB().insertAssoziationen(list);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}