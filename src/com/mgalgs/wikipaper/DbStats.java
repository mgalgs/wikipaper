package com.mgalgs.wikipaper;

public final class DbStats {
	public int numArticles;
	public int numUnusedArticles;
	public String maxUsedArticle;
	public int maxUsedArticleUses;

	DbStats(int nArticles_, int nUnusedArticles_, String maxUsedArticle_,
			int maxUsedArticleUses_) {
		numArticles = nArticles_;
		numUnusedArticles = nUnusedArticles_;
		maxUsedArticle = maxUsedArticle_;
		maxUsedArticleUses = maxUsedArticleUses_;
	}
}
