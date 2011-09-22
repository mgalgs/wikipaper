package com.mgalgs.wikipaper;

import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import android.util.Log;

public class WikiParse {
    public static String extractArticleSummaryFromJSON(JSONObject json) {
		try {
			String summaryhtml = json.getJSONObject("parse").getJSONObject("text").getString("*");
			Log.d(WikiPaper.WP_LOGTAG, "trying to parse...");
			Document doc = Jsoup.parse(summaryhtml);
			Log.d(WikiPaper.WP_LOGTAG, "trying select els...");
			Elements els = doc.select("p");
			Log.d(WikiPaper.WP_LOGTAG, "trying to get text...");
			String summary = els.first().text();
			Log.d(WikiPaper.WP_LOGTAG, "ready to go!!!...");
			return summary;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
    }
    
    public static JSONObject getArticleJSON(int articleId) {
        QueryString qs = new QueryString("http://en.wikipedia.org/w/api.php");
        qs.add("action", "parse");
        qs.add("format", "json");
        qs.add("pageid", Integer.toString(articleId));
        qs.add("prop", "text");

        Log.d(WikiPaper.WP_LOGTAG, "trying to get article json");
        return RestClient.connect(qs.getQuery());
    }

    public static String getRandomArticleSummary() {
    	JSONObject rand_article_json = getRandomArticleJSON();
    	int article_id;
		try {
			article_id = rand_article_json.getJSONObject("query").getJSONArray("random").getJSONObject(0).getInt("id");
			JSONObject article_json = getArticleJSON(article_id);
			return extractArticleSummaryFromJSON(article_json);
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
    }
    
    public static JSONObject getRandomArticleJSON() {
        QueryString qs = new QueryString("http://en.wikipedia.org/w/api.php");
        qs.add("action", "query");
        qs.add("list", "random");
        qs.add("format", "json");
        qs.add("rnnamespace", "0");
        qs.add("rnlimit", "1");

        Log.d(WikiPaper.WP_LOGTAG, "trying to get random article json");
        return RestClient.connect(qs.getQuery());
    }
}
