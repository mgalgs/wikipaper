package com.mgalgs.wikipaper;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
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
		} catch (Exception e) {
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

        return RestClient.connect(qs.getQuery());
    }

    public static List<Article> getRandomArticles(int nArticles) {
    	List<Article> alist = new ArrayList<Article>();
		try {
			JSONArray jsonrandroot = getRandomArticlesJSON(nArticles)
					.getJSONObject("query").getJSONArray("random");
			Log.i(WikiPaper.WP_LOGTAG, String.format(
					"Downloaded %d article json specs", nArticles));
			for (int i = 0; i < nArticles; i++) {
				JSONObject j = jsonrandroot.getJSONObject(i);
				JSONObject article_json = getArticleJSON(j.getInt("id"));
				if (article_json == null) {
					Log.e(WikiPaper.WP_LOGTAG, "Got null from download... :(");
					continue;
				}
				Log.i(WikiPaper.WP_LOGTAG,
						String.format("Downloaded article %d/%d", i+1, nArticles));

				Article a = new Article();
				a.summary = extractArticleSummaryFromJSON(article_json);
				a.title = j.getString("title");
				alist.add(a);
			}
			return alist;
		} catch (Exception e) {
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
