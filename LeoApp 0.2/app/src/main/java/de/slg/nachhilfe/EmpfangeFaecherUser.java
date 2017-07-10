package de.slg.nachhilfe;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;


class EmpfangeFaecherUser extends AsyncTask< Void, Void ,String[] > {
    protected String[] doInBackground(Void... params) {
        String[] result = new String[0];
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("http://moritz.liegmanns.de/nachhilfeboerse/getFachUser1_0.php").openConnection();
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            String erg = "";
            String l;
            while ((l = reader.readLine()) != null)
                erg += l;
            result = erg.split("_next_");
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

}
