package com.mgalgs.wikipaper;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import android.util.Log;

public class WikiParse {
	
	private final static String summarySelector = "p";
	private final static String disambigSummarySelector = summarySelector + " + ul";
	private final static String filterSelector = "table";
	private final static String disambigTest = "#disambigbox";

	public static String extractArticleSummaryFromJSON(JSONObject json) {
		try {
			String summaryhtml = json.getJSONObject("parse").getJSONObject("text").getString("*");
			Document doc = Jsoup.parse(summaryhtml);
			// see if this is a disambiguation page:
			if (!doc.select(disambigTest).isEmpty()) {
				Log.d(WikiPaper.WP_LOGTAG, "This is a disambiguation page!");
				String txt1 = doc.select(summarySelector).not(filterSelector).first().text();
				String txt2 = doc.select(disambigSummarySelector).not(filterSelector).first().text();
				Log.d(WikiPaper.WP_LOGTAG, "txt1 and txt2 are: " + txt1 + " :AND: " + txt2);
				// this is a disambiguation page
				return txt1 + txt2;
			} else {
				// this is a normal article
				return doc.select(summarySelector).not(filterSelector).first().text();
			}
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
			Log.d(WikiPaper.WP_LOGTAG, String.format(
					"Downloaded %d article json specs", nArticles));
			for (int i = 0; i < nArticles; i++) {
				JSONObject j = jsonrandroot.getJSONObject(i);
				JSONObject article_json = getArticleJSON(j.getInt("id"));
				if (article_json == null) {
					Log.e(WikiPaper.WP_LOGTAG, "Got null from download... :(");
					continue;
				}
				Log.d(WikiPaper.WP_LOGTAG,
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
