import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

//import com.mohaps.fetch.Fetcher;
//import com.mohaps.fetch.HeadResult;

/**
 * This class is thread safe.
 * 
 * @author Peter Karich
 */
public class ArticleTextExtractor {

	// Interessting nodes
	private static final Pattern NODES = Pattern
			.compile("p|div|td|h1|h2|article|section");
	// Unlikely candidates
	private String unlikelyStr;
	private Pattern UNLIKELY;
	// Most likely positive candidates
	private String positiveStr;
	private Pattern POSITIVE;
	// Most likely negative candidates
	private String negativeStr;
	private Pattern NEGATIVE;
	private static final Pattern NEGATIVE_STYLE = Pattern
			.compile("hidden|display: ?none|font-size: ?small");
	private static final Set<String> IGNORED_TITLE_PARTS = new LinkedHashSet<String>() {
		{
			add("hacker news");
			add("facebook");
		}
	};
	private static final OutputFormatter DEFAULT_FORMATTER = new OutputFormatter();
	private OutputFormatter formatter = DEFAULT_FORMATTER;

	//private Fetcher fetcher;
	public ArticleTextExtractor() {
		//this.fetcher = fetcher;
		setUnlikely("com(bx|ment|munity)|dis(qus|cuss)|e(xtra|[-]?mail)|foot|"
				+ "header|menu|re(mark|ply)|rss|sh(are|outbox)|sponsor"
				+ "a(d|ll|gegate|rchive|ttachment)|(pag(er|ination))|popup|print|"
				+ "login|si(debar|gn|ngle)");
		setPositive("(^(body|content|h?entry|main|page|post|text|blog|story|haupt))"
				+ "|arti(cle|kel)|instapaper_body");
		setNegative("nav($|igation)|user|com(ment|bx)|(^com-)|contact|"
				+ "foot|masthead|(me(dia|ta))|outbrain|promo|related|scroll|(sho(utbox|pping))|"
				+ "sidebar|sponsor|tags|tool|widget|player|disclaimer|toc|infobox|vcard");
	}

	public ArticleTextExtractor setUnlikely(String unlikelyStr) {
		this.unlikelyStr = unlikelyStr;
		UNLIKELY = Pattern.compile(unlikelyStr);
		return this;
	}

	public ArticleTextExtractor addUnlikely(String unlikelyMatches) {
		return setUnlikely(unlikelyStr + "|" + unlikelyMatches);
	}

	public ArticleTextExtractor setPositive(String positiveStr) {
		this.positiveStr = positiveStr;
		POSITIVE = Pattern.compile(positiveStr);
		return this;
	}

	public ArticleTextExtractor addPositive(String pos) {
		return setPositive(positiveStr + "|" + pos);
	}

	public ArticleTextExtractor setNegative(String negativeStr) {
		this.negativeStr = negativeStr;
		NEGATIVE = Pattern.compile(negativeStr);
		return this;
	}

	public ArticleTextExtractor addNegative(String neg) {
		setNegative(negativeStr + "|" + neg);
		return this;
	}

	public void setOutputFormatter(OutputFormatter formatter) {
		this.formatter = formatter;
	}

	public ExtractedContent extractContent(ExtractedContent res, Document doc,
			OutputFormatter formatter) throws Exception {
		if (doc == null)
			throw new NullPointerException("missing document");

		res.setTitle(extractTitle(doc));
		res.setDescription(extractDescription(doc));
		res.setCanonicalUrl(extractCanonicalUrl(doc));

		// now remove the clutter
		prepareDocument(doc);

		// init elements
		Collection<Element> nodes = getNodes(doc);
		int maxWeight = 0;
		Element bestMatchElement = null;
		for (Element entry : nodes) {
			int currentWeight = getWeight(entry);
			if (currentWeight > maxWeight) {
				maxWeight = currentWeight;
				bestMatchElement = entry;
				if (maxWeight > 200)
					break;
			}
		}

		// mohaps: this promotes the parent of best match to the best match element
		// if enough siblings of current best match have close childweight scores
		// fix was needed for url : 
		if (bestMatchElement != null) {
			Element bestParent = bestMatchElement.parent();
			if (bestParent != null) {
				String childWeightStr = bestParent.attr("childweight");
				int childWeight = 0;
				if (childWeightStr != null && !childWeightStr.isEmpty()) {
					childWeight = Integer.parseInt(childWeightStr);
				}

				List<Node> childNodes = bestParent.childNodes();
				int low = childWeight - 10;
				if (low < 0) {
					low = 0;
				}
				int high = childWeight + 30;
				double siblingScore = 0.0;
				for (Node childNode : childNodes) {
					if (childNode instanceof Element) {
						Element childElement = (Element) childNode;
						if (childElement != bestMatchElement) {
							String cWeight = childElement.attr("childweight");
							int thisChildWeight = 0;
							
							if(cWeight != null && cWeight.trim().length() > 0) { thisChildWeight = Integer.parseInt(childElement
									.attr("childweight")); }
							if (thisChildWeight > low) {
								siblingScore += 1.0;
								if (thisChildWeight <= high) {
									siblingScore += 1.0;
								}
							}
						}

						if (siblingScore >= 3.0) {
							System.out.println(">> Parent has way too many eligible siblings. promoting parent to main element");
							bestMatchElement = bestParent;
							break;
						}
					}
				}

			}
		}

		if (bestMatchElement != null) {
			Element imgEl = determineImageSource(bestMatchElement);
			if (imgEl != null) {
				//System.out.println("--> found image url : "+imgEl.attr("src"));
				res.setImageUrl(SHelper.replaceSpaces(imgEl.attr("src")));
				// TODO remove parent container of image if it is contained in
				// bestMatchElement
				// to avoid image subtitles flooding in
			} else {
				imgEl = determineImageSource(doc.body());
				if(imgEl != null) {
					//System.out.println("--> found image url (in body) : "+imgEl.attr("src"));
					res.setImageUrl(SHelper.replaceSpaces(imgEl.attr("src")));
				}
			}

			// clean before grabbing text
			String text = formatter.getFormattedText(bestMatchElement);
			text = removeTitleFromText(text, res.getTitle());
			// this fails for short facebook post and probably tweets:
			// text.length() > res.getDescription().length()
			if (text.length() > res.getTitle().length()) {
				res.setText(text);
				// print("best element:", bestMatchElement);
			}
		}

		if (res.getImageUrl().isEmpty()) {
			res.setImageUrl(extractImageUrl(doc));
		}

		res.setRssUrl(extractRssUrl(doc));
		res.setVideoUrl(extractVideoUrl(doc));
		res.setFaviconUrl(extractFaviconUrl(doc));
		res.setKeywords(extractKeywords(doc));

		// mohaps: hack to get absolute url of image
		String imgUrl = res.getImageUrl();
		
		if(imgUrl.isEmpty()){ return res; }
		
		if (!(imgUrl.startsWith("http://") || imgUrl.startsWith("https://"))) {
			// System.out.println(">> relative img url : "+imgUrl);
			
			if (imgUrl.startsWith("/")) {
				if(!imgUrl.startsWith("//"))
				{
					String rootUrl = SHelper.extractDomain(res.getOriginalUrl(),
							false);
					imgUrl = rootUrl + imgUrl;
				}
			} else if(!imgUrl.startsWith("./") || !imgUrl.startsWith("../")){
				String originalUrl = res.getOriginalUrl();
				if (originalUrl.endsWith("/")) {
					imgUrl = originalUrl + imgUrl;
				} else {
					imgUrl = imgUrl.split("\\?")[0];
					int index = originalUrl.lastIndexOf('/', 8);
					if(index >= 0) { 
						imgUrl = originalUrl.substring(0, index) + "/" + imgUrl;
					} else {
						imgUrl = originalUrl + "/" + imgUrl;
					}
				}

			}
		}
		if (!imgUrl.startsWith("http")) {
			imgUrl = new URL(res.getOriginalUrl()).getProtocol().toString()
					+ "://" + imgUrl;
		}
		res.setImageUrl(SHelper.getLargestPossibleImageUrl(imgUrl)); 
		return res;
	}

	protected String extractTitle(Document doc) {
		String title = cleanTitle(doc.title());
		if (title.isEmpty()) {
			title = SHelper.innerTrim(doc.select("head title").text());
			if (title.isEmpty()) {
				title = SHelper.innerTrim(doc.select("head meta[name=title]")
						.attr("content"));
				if (title.isEmpty()) {
					title = SHelper.innerTrim(doc.select(
							"head meta[property=og:title]").attr("content"));
					if (title.isEmpty()) {
						title = SHelper.innerTrim(doc.select(
								"head meta[name=twitter:title]")
								.attr("content"));
					}
				}
			}
		}
		return title;
	}

	protected String extractCanonicalUrl(Document doc) {
		String url = SHelper.replaceSpaces(doc.select(
				"head link[rel=canonical]").attr("href"));
		if (url.isEmpty()) {
			url = SHelper.replaceSpaces(doc
					.select("head meta[property=og:url]").attr("content"));
			if (url.isEmpty()) {
				url = SHelper.replaceSpaces(doc.select(
						"head meta[name=twitter:url]").attr("content"));
			}
		}
		return url;
	}

	protected String extractDescription(Document doc) {
		String description = SHelper.innerTrim(doc.select(
				"head meta[name=description]").attr("content"));
		if (description.isEmpty()) {
			description = SHelper.innerTrim(doc.select(
					"head meta[property=og:description]").attr("content"));
			if (description.isEmpty()) {
				description = SHelper.innerTrim(doc.select(
						"head meta[name=twitter:description]").attr("content"));
			}
		}
		return description;
	}

	protected Collection<String> extractKeywords(Document doc) {
		String content = SHelper.innerTrim(doc.select(
				"head meta[name=keywords]").attr("content"));

		if (content != null) {
			if (content.startsWith("[") && content.endsWith("]"))
				content = content.substring(1, content.length() - 1);

			String[] split = content.split("\\s*,\\s*");
			if (split.length > 1 || (split.length > 0 && !"".equals(split[0])))
				return Arrays.asList(split);
		}
		return Collections.emptyList();
	}

	/**
	 * Tries to extract an image url from metadata if determineImageSource
	 * failed
	 * 
	 * @return image url or empty str
	 */
	protected String extractImageUrl(Document doc) {
		// use open graph tag to get image
		String imageUrl = SHelper.replaceSpaces(doc.select(
				"head meta[property=og:image]").attr("content"));
		if (imageUrl.isEmpty()) {
			imageUrl = SHelper.replaceSpaces(doc.select(
					"head meta[name=twitter:image]").attr("content"));
			if (imageUrl.isEmpty()) {
				// prefer link over thumbnail-meta if empty
				imageUrl = SHelper.replaceSpaces(doc.select(
						"link[rel=image_src]").attr("href"));
				if (imageUrl.isEmpty()) {
					imageUrl = SHelper.replaceSpaces(doc.select(
							"head meta[name=thumbnail]").attr("content"));
				}
			}
		}
		System.out.println(" >> imageUrl : [" + imageUrl + "]");
		return imageUrl;
	}

	protected String extractRssUrl(Document doc) {
		return SHelper.replaceSpaces(doc.select("link[rel=alternate]")
				.select("link[type=application/rss+xml]").attr("href"));
	}

	protected String extractVideoUrl(Document doc) {
		return SHelper.replaceSpaces(doc.select("head meta[property=og:video]")
				.attr("content"));
	}

	protected String extractFaviconUrl(Document doc) {
		String faviconUrl = SHelper.replaceSpaces(doc.select(
				"head link[rel=icon]").attr("href"));
		if (faviconUrl.isEmpty()) {
			faviconUrl = SHelper.replaceSpaces(doc.select(
					"head link[rel^=shortcut],link[rel$=icon]").attr("href"));
		}
		return faviconUrl;
	}

	/**
	 * Weights current element. By matching it with positive candidates and
	 * weighting child nodes. Since it's impossible to predict which exactly
	 * names, ids or class names will be used in HTML, major role is played by
	 * child nodes
	 * 
	 * @param e
	 *            Element to weight, along with child nodes
	 */
	protected int getWeight(Element e) {
		int weight = calcWeight(e);
		weight += (int) Math.round(e.ownText().length() / 100.0 * 10);
		int childWeight = weightChildNodes(e);
		weight += childWeight;
		e.attr("childWeight", Integer.toString(childWeight));
		return weight;
	}

	/**
	 * Weights a child nodes of given Element. During tests some difficulties
	 * were met. For instanance, not every single document has nested paragraph
	 * tags inside of the major article tag. Sometimes people are adding one
	 * more nesting level. So, we're adding 4 points for every 100 symbols
	 * contained in tag nested inside of the current weighted element, but only
	 * 3 points for every element that's nested 2 levels deep. This way we give
	 * more chances to extract the element that has less nested levels,
	 * increasing probability of the correct extraction.
	 * 
	 * @param rootEl
	 *            Element, who's child nodes will be weighted
	 */
	protected int weightChildNodes(Element rootEl) {
		int weight = 0;
		Element caption = null;
		List<Element> pEls = new ArrayList<Element>(5);
		for (Element child : rootEl.children()) {
			String ownText = child.ownText();
			int ownTextLength = ownText.length();
			if (ownTextLength < 20)
				continue;

			if (ownTextLength > 200)
				weight += Math.max(50, ownTextLength / 10);

			if (child.tagName().equals("h1") || child.tagName().equals("h2")) {
				weight += 30;
			} else if (child.tagName().equals("div")
					|| child.tagName().equals("p")) {
				weight += calcWeightForChild(child, ownText);
				if (child.tagName().equals("p") && ownTextLength > 50)
					pEls.add(child);

				if (child.className().toLowerCase().equals("caption"))
					caption = child;
			}
		}

		// use caption and image
		if (caption != null)
			weight += 30;

		if (pEls.size() >= 2) {
			for (Element subEl : rootEl.children()) {
				if ("h1;h2;h3;h4;h5;h6".contains(subEl.tagName())) {
					weight += 20;
					// headerEls.add(subEl);
				} else if ("table;li;td;th".contains(subEl.tagName())) {
					addScore(subEl, -30);
				}

				if ("p".contains(subEl.tagName()))
					addScore(subEl, 30);
			}
		}
		return weight;
	}

	public void addScore(Element el, int score) {
		int old = getScore(el);
		setScore(el, score + old);
	}

	public int getScore(Element el) {
		int old = 0;
		try {
			old = Integer.parseInt(el.attr("gravityScore"));
		} catch (Exception ex) {
		}
		return old;
	}

	public void setScore(Element el, int score) {
		el.attr("gravityScore", Integer.toString(score));
	}

	private int calcWeightForChild(Element child, String ownText) {
		int c = SHelper.count(ownText, "&quot;");
		c += SHelper.count(ownText, "&lt;");
		c += SHelper.count(ownText, "&gt;");
		c += SHelper.count(ownText, "px");
		int val;
		if (c > 5)
			val = -30;
		else
			val = (int) Math.round(ownText.length() / 25.0);

		addScore(child, val);
		return val;
	}

	private int calcWeight(Element e) {
		int weight = 0;
		if (POSITIVE.matcher(e.className()).find())
			weight += 35;

		if (POSITIVE.matcher(e.id()).find())
			weight += 40;

		if (UNLIKELY.matcher(e.className()).find())
			weight -= 20;

		if (UNLIKELY.matcher(e.id()).find())
			weight -= 20;

		if (NEGATIVE.matcher(e.className()).find())
			weight -= 50;

		if (NEGATIVE.matcher(e.id()).find())
			weight -= 50;

		String style = e.attr("style");
		if (style != null && !style.isEmpty()
				&& NEGATIVE_STYLE.matcher(style).find())
			weight -= 50;
		return weight;
	}

	public Element determineImageSource(Element el) {
		int maxWeight = 0;
		Element maxNode = null;
		Elements els = el.select("img");
		if (els.isEmpty()) {
			if(el.parent() != null) {
				els = el.parent().select("img");
			}
			if(els.isEmpty()) {
				if(el.parent().parent() != null) {
					els = el.parent().parent().select("img");
				}
			}
		}
		//System.out.println(">>>> "+els.size()+" images found ");
		double score = 1;
		boolean firstIter = true;
		for (Element e : els) {
			
			String sourceUrl = e.attr("src");
			if(sourceUrl == null || sourceUrl.isEmpty()) {
				sourceUrl = e.attr("data-src");
			} 

			if(sourceUrl == null || sourceUrl.isEmpty()) {
				continue;
			} 
			//System.out.println(" ==>>> considering image : [" + sourceUrl+ "] e = "+e+"...");
			
			if(sourceUrl.endsWith(".gif") || sourceUrl.contains(".gif")){ continue; }
					
			if (sourceUrl.isEmpty() || isAdImage(sourceUrl) || sourceUrl.endsWith("spacer.gif") || sourceUrl.endsWith("PinExt.png")) {
				continue;
			}
			if(sourceUrl.indexOf("/widget") > 0 || sourceUrl.indexOf("/icon") > 0) {
				continue;
			}
			if (firstIter) {
				maxNode = e;
				firstIter = false;
			}

			int weight = 0;
			String imgClass = e.attr("class");
			if(imgClass != null && !imgClass.isEmpty()) {
				if(imgClass.startsWith("main")) {
					maxNode = e;
					break;
				}
			}
			if (sourceUrl.indexOf("gravatar.com/") > 0) {
				//System.out.println("  ------------> PROFILE IMAGE ....");
				weight -= 60;
			}
			boolean heightNotFound = true;
			boolean widthNotFound = true;
			try {
				boolean notFound = true;
				String s = e.attr("height");
				int height = 0;
				if (s != null && s.length() > 0) {
					height = Integer.parseInt(s);
					//System.out.println(">> Height : "+height);
					notFound = false;
				} else {
					//System.out.println(">> Parsing "+e);
					height = parseStyleForIntValue(e, "height");
					if(height > 0) {
						notFound = false;
					} 
				}
				heightNotFound = notFound;
				if (height > 100)
					weight += 20;
				else if (height < 100 && !notFound)
					weight -= 20;
				else if (height < 200 && !notFound)
					weight -= 10;
			} catch (Exception ex) {
			}

			try {
				boolean notFound = true;
				String s = e.attr("width");
				int width = 0;
				if (s != null && s.length() > 0) {
					width = Integer.parseInt(s);
					notFound = false;
				} else {
					width = parseStyleForIntValue(e, "width");
					if(width > 0) {
						notFound = false;
					}
				}
				widthNotFound = notFound;

				if (width > 300)
					weight += 200;
				else if(width > 100)
					weight += 80;
				else if (width < 50 && !notFound)
					weight += 50;
				else if (width < 100 && !notFound)
					weight -= 40;
				else if (width < 200 && !notFound)
					weight -= 30;
				else if (width < 300 && !notFound)
					weight -= 20;
			} catch (Exception ex) {
			}

            /*
			if(heightNotFound && widthNotFound && fetcher != null) {
				try {
					HeadResult head = fetcher.fetchHead(sourceUrl, 500);
					if(head.isSuccess()) {
						long contentLength = head.getContentLength();
						if(contentLength >= 200 * 1024) {
							weight += 1000;
						} else if(contentLength >= 200 * 1024) {
							weight += 400;
						} else if(contentLength >= 100 * 1024) {
							weight += 200;
						} else if(contentLength >= 80 * 1024) {
							weight += 100;
						} else if(contentLength >= 50 * 1024) {
							weight += 50;
						}
					} else {
						weight -= 500;
					}
				} catch (Exception ex) {
					
				}
			}
            */

			String alt = e.attr("alt");
			if (alt.length() > 55)
				weight += 50;
			else if (alt.length() > 35)
				weight += 20;

			String title = e.attr("title");
			if (title.length() > 55)
				weight += 50;
			else if (title.length() > 35)
				weight += 20;
			
			if (e.parent() != null) {
				String rel = e.parent().attr("rel");
				if (rel != null && rel.contains("nofollow"))
					weight -= 40;
			}
			weight = (int) (weight * score);

			//System.out.println(" Image (" + sourceUrl + ") Weight : " + weight
					//+ " Score : " + score);
			if (weight > maxWeight) {
				maxWeight = weight;
				maxNode = e;
				score = score / 2;
			}
		}
		return maxNode;
	}

	private int parseStyleForIntValue(Element e, String name) {
		int value = 0;
		String style = e.attr("style");
		if(style != null && style.length() > 0){
			//System.out.println(">> Parsing Style ["+style+"] for "+name);
		}
		return value;
	}

	/**
	 * Prepares document. Currently only stipping unlikely candidates, since
	 * from time to time they're getting more score than good ones especially in
	 * cases when major text is short.
	 * 
	 * @param doc
	 *            document to prepare. Passed as reference, and changed inside
	 *            of function
	 */
	protected void prepareDocument(Document doc) {
		// stripUnlikelyCandidates(doc);
		removeScriptsAndStyles(doc);
	}

	/**
	 * Removes unlikely candidates from HTML. Currently takes id and class name
	 * and matches them against list of patterns
	 * 
	 * @param doc
	 *            document to strip unlikely candidates from
	 */
	protected void stripUnlikelyCandidates(Document doc) {
		for (Element child : doc.select("body").select("*")) {
			String className = child.className().toLowerCase();
			String id = child.id().toLowerCase();

			if (NEGATIVE.matcher(className).find()
					|| NEGATIVE.matcher(id).find()) {
				// print("REMOVE:", child);
				child.remove();
			}
		}
	}

	private Document removeScriptsAndStyles(Document doc) {
		Elements scripts = doc.getElementsByTag("script");
		for (Element item : scripts) {
			item.remove();
		}

		Elements noscripts = doc.getElementsByTag("noscript");
		for (Element item : noscripts) {
			item.remove();
		}

		Elements styles = doc.getElementsByTag("style");
		for (Element style : styles) {
			style.remove();
		}

		return doc;
	}

	private void print(Element child) {
		print("", child, "");
	}

	private void print(String add, Element child) {
		print(add, child, "");
	}

	private void print(String add1, Element child, String add2) {
		System.out.println(add1 + " " + child.nodeName() + " id=" + child.id()
				+ " class=" + child.className() + " text=" + child.text() + " "
				+ add2);
	}

	private boolean isAdImage(String imageUrl) {
		return imageUrl.endsWith(".gif")
				||SHelper.count(imageUrl, "ad") >= 2 
				|| imageUrl.indexOf("scorecardresearch.com") > 0
				|| imageUrl.indexOf("doubleclick.com") > 0
				|| imageUrl.endsWith("/logo.jpg")
				|| imageUrl.endsWith("/logo.png");
				
	}

	/**
	 * Match only exact matching as longestSubstring can be too fuzzy
	 */
	public String removeTitleFromText(String text, String title) {
		// don't do this as its terrible to read
		// int index1 = text.toLowerCase().indexOf(title.toLowerCase());
		// if (index1 >= 0)
		// text = text.substring(index1 + title.length());
		// return text.trim();
		return text;
	}

	/**
	 * based on a delimeter in the title take the longest piece or do some
	 * custom logic based on the site
	 * 
	 * @param title
	 * @param delimeter
	 * @return
	 */
	private String doTitleSplits(String title, String delimeter) {
		String largeText = "";
		int largetTextLen = 0;
		String[] titlePieces = title.split(delimeter);

		// take the largest split
		for (String p : titlePieces) {
			if (p.length() > largetTextLen) {
				largeText = p;
				largetTextLen = p.length();
			}
		}

		largeText = largeText.replace("&raquo;", " ");
		largeText = largeText.replace("\u00BB", " ");
		return largeText.trim();
	}

	/**
	 * @return a set of all important nodes
	 */
	public Collection<Element> getNodes(Document doc) {
		Map<Element, Object> nodes = new LinkedHashMap<Element, Object>(64);
		int score = 100;
		for (Element el : doc.select("body").select("*")) {
			if (NODES.matcher(el.tagName()).matches()) {
				nodes.put(el, null);
				setScore(el, score);
				score = score / 2;
			}
		}
		return nodes.keySet();
	}

	public String cleanTitle(String title) {
		StringBuilder res = new StringBuilder();
		// int index = title.lastIndexOf("|");
		// if (index > 0 && title.length() / 2 < index)
		// title = title.substring(0, index + 1);

		int counter = 0;
		String[] strs = title.split("\\|");
		for (String part : strs) {
			if (IGNORED_TITLE_PARTS.contains(part.toLowerCase().trim()))
				continue;

			if (counter == strs.length - 1 && res.length() > part.length())
				continue;

			if (counter > 0)
				res.append("|");

			res.append(part);
			counter++;
		}

		return SHelper.innerTrim(res.toString());
	}
}
