package shopInfoSpider;

import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import Pipeline.sqlPipeline;
import Utils.DbPoolConnection;
import Utils.JdbcUtils;
import us.codecraft.webmagic.Page;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.processor.PageProcessor;
import us.codecraft.webmagic.selector.Html;

public class DazongPageProcessor implements PageProcessor {

	public static String shopIdFirst_food;
	public static String shopIdFirst_ktv;

	public static DbPoolConnection dbp;

	public static final int TYPE_KTV = 1;
	public static final int TYPE_FOOD = 2;
	public static final int TYPE_HOTEL = 3;
	public static final int TYPE_MOVIE = 4;
	public static final int TYPE_JSON = 5;
	public static final int TYPE_SHOP = 6;
	public static final int TYPE_FINDDEAL = 7;
	public static final int TYPE_DEALINFO = 8;
	public static final int TYPE_DEALCOMMENT = 9;
	public static final int TYPE_SHOPCOMMENT = 10;

	public static String NOWDATE;

	public static final String URLBASE_SHOP = "http://www.dianping.com/shop/";
	public static final String URLBASE_JSON = "http://www.dianping.com/ajax/json/shop/wizard/BasicHideInfoAjaxFP?_nr_force=";
	public static String unixTime;
	public static Connection connection;
	public static int counter = 1;

	public static String tableName_food = "`dazongdianping`.`dazhongdianping_shopinfo_single_ktv_20160507`";

	public static List<Map<String, Object>> cityIds;
	public static List<Map<String, Object>> cookies;
	public static int cookieNum = 0;
	public static int nowCookie = 0;

	public static int citySize;

	private Site site = Site
			.me()
			.setRetryTimes(5)
			.setCycleRetryTimes(3)
			.setSleepTime(100)
			.setTimeOut(5000)
			.setDomain(".dianping.com")
			.setUserAgent(
					"Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.152 Safari/537.36");

	@Override
	public Site getSite() {
		return site;
	}

	public void setSite(Site site) {
		this.site = site;
	}

	@Override
	public void process(Page page) {
		Request request = page.getRequest();
		Html html = page.getHtml();
		int type = (int) request.getExtra("type");
		int type2 = (int) request.getExtra("type2");

		switch (type) {
		case TYPE_HOTEL: {
			int pageNum = (int) request.getExtra("pageNum") + 1;
			String cityId = (String) request.getExtra("cityId");
			// System.out.println("food!");
			List<String> shopIds = html
					.xpath("//h2[@class='hotel-name']/a[@class='hotel-name-link']/@href")
					.all();
			System.out.println(shopIds.toString());
			int size = shopIds.size();
			if (size > 0
					&& (null == shopIdFirst_food || shopIds.get(0).equals(
							shopIdFirst_food) == false)) {
				shopIdFirst_food = shopIds.get(0);
				// if it has shops
				if (size >= 15) {
					Request requestTo = new Request();
					requestTo.setUrl("http://www.dianping.com/" + cityId
							+ "/hotel/p" + pageNum);
					requestTo.putExtra("pageNum", pageNum);
					requestTo.putExtra("type", TYPE_HOTEL);
					requestTo.putExtra("type2", 0);
					requestTo.putExtra("cityId", cityId);
					page.addTargetRequest(requestTo);
				}
				// find shopinfo
				detailPageProcess detailPageProcess = new detailPageProcess();
				if (nowCookie >= cookieNum) {
					nowCookie %= cookieNum;
				}
				String cookie = (String) cookies.get(nowCookie).get("key");
				nowCookie++;
				detailPageProcess.setSite(detailPageProcess.getSite()
						.addCookie("_hc.v", cookie));
				Spider tems = Spider.create(detailPageProcess).addPipeline(
						new sqlPipeline());
				for (String shopId : shopIds) {
					Request shopInfoRequest2 = new Request();
					shopInfoRequest2.setUrl(shopId);
					shopId = shopId.substring(29, shopId.length());
					shopInfoRequest2.putExtra("type", TYPE_SHOP);
					shopInfoRequest2.putExtra("type2", TYPE_HOTEL);
					shopInfoRequest2.putExtra("shopId", shopId);
					tems.addRequest(shopInfoRequest2);
				}
				tems.thread(5).run();
				tems.close();
				tems = null;
				shopIds = null;
			}
			break;
		}
		default:
			System.out.println("fail");
			break;
		}
		request = null;
		page.setSkip(true);
	}

	public static void main(String[] args) throws SQLException {
		Site temsite = Site
				.me()
				.setRetryTimes(5)
				.setCycleRetryTimes(3)
				.setSleepTime(100)
				.setTimeOut(5000)
				.setDomain(".dianping.com")
				.setUserAgent(
						"Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/42.0.2311.152 Safari/537.36");

		dbp = DbPoolConnection.getInstance();
		unixTime = String.valueOf(System.currentTimeMillis());
		NOWDATE = GetNowDate();
		// 查询所有cookie
		cookies = JdbcUtils.findModeResult("select `key` from cookies3", null,
				dbp.getConnection());
		cookieNum = cookies.size();
		cityIds = JdbcUtils
				.findModeResult(
						"select cityId,cityNameCh,cityNameEn from dazhongdianping_city",
						null, dbp.getConnection());
		citySize = cityIds.size();

		DazongPageProcessor temPageProcessor = new DazongPageProcessor();
		sqlPipeline sqlPipeline = new sqlPipeline();
		for (int i = 0; i < citySize; i++) {

			// cookie 循环使用
			if (nowCookie >= cookieNum) {
				nowCookie %= cookieNum;
			}
			String cookie = (String) cookies.get(nowCookie).get("key");
			temPageProcessor.setSite(temsite.addCookie("_hc.v", cookie));
			String cityId = (String) cityIds.get(i).get("cityId");

			String cityNameEn = (String) cityIds.get(i).get("cityNameEn");

			// hotel 对于每个城市 request构建 ，从hotel首页进入
			Request request = new Request("http://www.dianping.com/"
					+ cityNameEn + "/hotel/p1");
			request.putExtra("pageNum", 1);
			request.putExtra("type", TYPE_HOTEL);
			request.putExtra("type2", 0);
			request.putExtra("cityId", cityNameEn);

			nowCookie++;
			Spider newSpider = Spider.create(temPageProcessor).addPipeline(
					sqlPipeline);
			newSpider.addRequest(request).thread(3).run();
			newSpider.close();
			newSpider = null;
			request = null;
			cookie = null;
		}
		dbp = null;
		System.out.println("done.");
	}

	/**
	 * 获得当前日期
	 * 
	 * @return
	 */
	public static String GetNowDate() {
		String temp_str = "";
		Date dt = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
		temp_str = sdf.format(dt);
		return temp_str;
	}

}
