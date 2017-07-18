package cd.connect.spring.servlet;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class Definition {
	private int priority;
	private String name;
	private Map<String, String> params = new HashMap<>();
	private List<String> urls = new ArrayList<>();

	public Definition param(String key, String value) {
		params.put(key, value);
		return this;
	}

	public Definition url(String url) {
		urls.add(url);

		return this;
	}

	public Definition priority(int pri) {
		this.priority = pri;
		return this;
	}

	public Definition name(String name) {
		this.name = name;
		return this;
	}

	public int getPriority() {
		return priority;
	}

	public String getName() {
		return name;
	}

	public Map<String, String> getParams() {
		return params;
	}

	public List<String> getUrls() {
		return urls;
	}
}
