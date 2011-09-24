package com.mgalgs.wikipaper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import android.util.Log;

public class WikiParse {
    public static String extractArticleSummaryFromJSON(JSONObject json) {
		try {
			String summaryhtml = json.getJSONObject("parse").getJSONObject("text").getString("*");
			Document doc = Jsoup.parse(summaryhtml);
			return doc.select("p").first().text();
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

    public static Article[] getRandomArticles(int nArticles) {
    	Article [] alist = new Article[nArticles];
		try {
			JSONArray jsonrandroot = getRandomArticlesJSON(nArticles)
					.getJSONObject("query").getJSONArray("random");
			for (int i = 0; i < nArticles; i++) {
				JSONObject j = jsonrandroot.getJSONObject(i);
				JSONObject article_json = getArticleJSON(j.getInt("id"));

				Article a = new Article();
				a.summary = extractArticleSummaryFromJSON(article_json);
				a.title = j.getString("title");
				alist[i] = a;
			}
			return alist;
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
    }
    
    public static JSONObject getRandomArticlesJSON(int nArticles) {
        QueryString qs = new QueryString("http://en.wikipedia.org/w/api.php");
        qs.add("action", "query");
        qs.add("list", "random");
        qs.add("format", "json");
        qs.add("rnnamespace", "0");
        qs.add("rnlimit", Integer.toString(nArticles));

        Log.d(WikiPaper.WP_LOGTAG, "trying to get random article json");
        return RestClient.connect(qs.getQuery());
    }
}
