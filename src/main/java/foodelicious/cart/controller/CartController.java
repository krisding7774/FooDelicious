package foodelicious.cart.controller;

import java.util.List;

import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import foodelicious.cart.model.CartBean;
import foodelicious.cart.service.CartService;
import foodelicious.cart.service.SearchService;
import foodelicious.discount.model.DiscountBean;
import foodelicious.discount.service.DiscountService;
import foodelicious.member.service.MemberService;
import foodelicious.orders.model.OrdersBean;
import foodelicious.product.model.Product;

@Controller
public class CartController {

	@Autowired
	private HttpSession session;

	@Autowired
	private CartService cartService;

	@Autowired
	private SearchService searchService;

	@Autowired
	private MemberService memberService;

	@Autowired
	private DiscountService discountService;

	@GetMapping("/shoppingCart")
	public String shoppingCart() {
		Long userId = (Long) session.getAttribute("userID");

		if (userId != null) {
			List<CartBean> carts = cartService.selectItem(userId);
			session.setAttribute("carts", carts);
			session.setAttribute("coin", getGoldCoin());
			session.setAttribute("priceTotal", originTotal());
			return "app.ShoppingCart";
		} else {
			return "app.LoginSystem";
		}
	}

	@ResponseBody
	@PostMapping("/shoppingCart/insert")
	public String insertItem(@RequestBody String jS) {
		Long userId = (Long) session.getAttribute("userID");

//		將JSON字串轉換成物件，方便從中獲取產品資料。
		JSONObject obj = JSON.parseObject(jS);
		Long productId = Long.parseLong(String.valueOf(obj.get("pid")));
		Integer quantity = Integer.parseInt(String.valueOf(obj.get("qty")));

//		如果使用者沒有登入，無法進入購物車中。
		if (userId == null) {
			return "{\"ans\":\"請先登入會員!!\"}";
		}

//		判斷購物車是否有重複商品
		Boolean repeat = false;

		List<CartBean> carts = cartService.selectItem(userId);

		for (CartBean cart : carts) {
			if (cart.getProductId() == productId) {
				Integer sum = cart.getQuantity() + quantity;
				Product product = cart.getProduct();
				if (sum > product.getProductStock()) {
					sum = product.getProductStock();
					return "已經到達庫存最大數量了(目前庫存共有 " + product.getProductStock() + " 件)";
				}
				cart.setCartId(cart.getCartId());
				cart.setMemberId(cart.getMemberId());
				cart.setProductId(cart.getProductId());
				cart.setQuantity(sum);
				cartService.insertAndUpdateItem(cart);
				repeat = true;
				break;
			}
		}

		if (repeat != true) {
			cartService.insertAndUpdateItem(new CartBean(userId, productId, quantity));
		}

		return "{\"ans\":\"" + "此項商品數量 " + quantity + " 個已加入購物車" + "\"}";
	}

	@ResponseBody
	@GetMapping("/shoppingCart/{productId}")
	public void deleteItem(@PathVariable Long productId) {
		List<CartBean> carts = cartService.selectItem((Long) session.getAttribute("userID"));

		for (CartBean cart : carts) {
			if (cart.getProductId() == productId) {
				cartService.deleteItem(cart.getCartId());
				break;
			}
		}
	}

	@ResponseBody
	@GetMapping("/shoppingCart/{productId}/{quantity}")
	public void updateItem(@PathVariable Long productId, @PathVariable Integer quantity) {
		List<CartBean> carts = cartService.selectItem((Long) session.getAttribute("userID"));

		for (CartBean cart : carts) {
			if (cart.getProductId() == productId) {
				cart.setQuantity(cart.getQuantity() + quantity);
				if (cart.getQuantity() > cart.getProduct().getProductStock()) {
					cart.setQuantity(cart.getProduct().getProductStock());
				} else if (cart.getQuantity() > 0) {
					cartService.insertAndUpdateItem(cart);
				} else {
					cart.setQuantity(1);
				}
				break;
			}
		}
	}

	@ResponseBody
	@GetMapping("/shoppingCart/show")
	public List<CartBean> showItem() {
		List<CartBean> carts = cartService.selectItem((Long) session.getAttribute("userID"));
		session.setAttribute("carts", carts);
		return carts;
	}

	@GetMapping("/cartToOrders")
	public String orders() {
		List<CartBean> carts = cartService.selectItem((Long) session.getAttribute("userID"));

		OrdersBean ordersBean = new OrdersBean();

//		計算購物車商品數量
		Integer count = 0;

		for (CartBean cart : carts) {
			ordersBean.setMemberId(cart.getMemberId());
			ordersBean.setOrdersName(cart.getMember().getMemberName());
			ordersBean.setOrdersPhone(cart.getMember().getMemberPhone());
			ordersBean.setOrdersAddress(cart.getMember().getMemberAddress());
			count++;
		}

		session.setAttribute("orders", ordersBean);
		session.setAttribute("count", count);
		return "app.Orders";
	}

	@ResponseBody
	@GetMapping("/shoppingCart/discountTotal/{discountName}/{coin}")
	public Integer discountTotal(@PathVariable(required = false) String discountName,
			@PathVariable(required = false) String coin) {
		List<DiscountBean> discounts = discountService.selectItem((Long) session.getAttribute("userID"));

		Integer currentCoin = Integer.parseInt(coin);
		Integer discountTotal = originTotal();

		if (discountName != null && discountName.length() != 0) {
			for (DiscountBean discount : discounts) {
				if (discount.getDiscountName().equals(discountName)) {
					discountTotal -= discount.getDiscountContent();
					session.setAttribute("discountId", discount.getDiscountId());
					break;
				}
			}
		}

		if (currentCoin != 0) {
			discountTotal -= currentCoin;
		}

		if (discountTotal < 0) {
			discountTotal = 0;
		}

		session.setAttribute("priceTotal", discountTotal);
		return discountTotal;
	}

	@ResponseBody
	@GetMapping("/getContent/{discountName}/{coin}")
	public Integer getDiscountContent(@PathVariable(required = false) String discountName,
			@PathVariable(required = false) String coin) {
		List<DiscountBean> discounts = discountService.selectItem((Long) session.getAttribute("userID"));
		Integer currentCoin = Integer.parseInt(coin);
		Integer discountContent = 0;

		if (discountName != null && discountName.length() != 0) {
			for (DiscountBean discount : discounts) {
				if (discount.getDiscountName().equals(discountName)) {
					discountContent += discount.getDiscountContent();
					break;
				}
			}
		}

		discountContent = currentCoin != 0 ? discountContent + currentCoin : discountContent;

		session.setAttribute("discountContent", discountContent);
		return discountContent;
	}

	@ResponseBody
	@GetMapping("/shoppingCart/insertDis")
	public String insertDis() {
		discountService.insertItem(new DiscountBean((Long) session.getAttribute("userID"), "TK888", 200));
		return "TK888";
	}

	@ResponseBody
	@GetMapping("/searchProduct/{name}")
	public List<Product> searchProduct(@PathVariable(name = "name") String productName) {
		return searchService.findByProductNameLike("%" + productName + "%");
	}

//	顯示初始金額
	public Integer originTotal() {
		List<CartBean> carts = cartService.selectItem((Long) session.getAttribute("userID"));
		Integer originTotal = 0;

		for (CartBean cart : carts) {
			originTotal += cart.getProduct().getProductPrice() * cart.getQuantity();
		}

		return originTotal < 1000 ? originTotal + 100 : originTotal;
	}

//	獲取使用者的金幣
	public Integer getGoldCoin() {
		Long userId = (Long) session.getAttribute("userID");
		return userId != null ? memberService.findByMemberId(userId).getMemberCoin() : 0;
	}

}
