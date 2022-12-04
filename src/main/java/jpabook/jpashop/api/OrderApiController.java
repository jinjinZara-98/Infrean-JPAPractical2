package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderItem;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.*;
import jpabook.jpashop.repository.order.query.OrderFlatDto;
import jpabook.jpashop.repository.order.query.OrderItemQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryDto;
import jpabook.jpashop.repository.order.query.OrderQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.*;


/**
 * V1. 엔티티 직접 노출
 * - 엔티티가 변하면 API 스펙이 변한다.
 * - 트랜잭션 안에서 지연 로딩 필요
 * - 양방향 연관관계 문제
 *
 * V2. 엔티티를 조회해서 DTO로 변환(fetch join 사용X)
 * - 트랜잭션 안에서 지연 로딩 필요
 * V3. 엔티티를 조회해서 DTO로 변환(fetch join 사용O)
 * - 페이징 시에는 N 부분을 포기해야함(대신에 batch fetch size? 옵션 주면 N -> 1 쿼리로 변경 가능)
 *
 * V4. JPA에서 DTO로 바로 조회, 컬렉션 N 조회 (1 + N Query)
 * - 페이징 가능
 * V5. JPA에서 DTO로 바로 조회, 컬렉션 1 조회 최적화 버전 (1 + 1 Query)
 * - 페이징 가능
 * V6. JPA에서 DTO로 바로 조회, 플랫 데이터(1Query) (1 Query)
 * - 페이징 불가능...
 *
 */
//주문한 내역과 주문안에 있는 상품명을 출력하는
@RestController
@RequiredArgsConstructor
public class OrderApiController {

    private final OrderRepository orderRepository;
    private final OrderQueryRepository orderQueryRepository;

    /**
     * V1. 엔티티 직접 노출
     * - Hibernate5Module 모듈 등록, LAZY=null 처리
     * - 양방향 관계 문제 발생 -> @JsonIgnore
     */
    @GetMapping("/api/v1/orders")
    public List<Order> ordersV1() {

        List<Order> all = orderRepository.findAll();

        /**
         * 강제 초기화하는 이유는 지연로딩이므로 프록시 객체가 아닌 진짜 객체를 가져오기 위해
         * 양방향관계는 jsonignore걸어줘야
         */
        for (Order order : all) {
            order.getMember().getName(); //Lazy 강제 초기화
            order.getDelivery().getAddress(); //Lazy 강제 초기환
            List<OrderItem> orderItems = order.getOrderItems();//핵심

            /**
             * Lazy 강제 초기화, 쥬뮨과 관련된 주문된 아이템 가져와 돌리면서 아이템들 다 이름 가져오며 초기화
             * Order클래스에서  orderItem가 1대 다 관계이므로, orderItem클래스에서 Iten도 지연로딩이므로
             * 주문하면 회원과 주문한 상품들과 배송, 주문한 상품들에서는 각각 주문한 상품의 정보
             * 상품 엔티티도 lazy 초기화해 json으로 정보 출력됨
             */
            orderItems.stream().forEach(o -> o.getItem().getName());
        }
        return all;
    }

    /**
     * DTO로 변환, 컬렉션 쓰면 쿼리가 많이 나가므로 최적화에 대해 더 고민해야함
     * @return
     */
    @GetMapping("/api/v2/orders")
    public List<OrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAll();

        //List<Order>를 List<OrderDto>로 변환
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return result;
    }

    /**
     * 패치 조인, 쿼리가 한 번 나감, JPQL쿼리문만 다름, 일대다 패치조인 하는 순간 페이징이 불가능하다는 단점
     * 일대다 쿼리 결과는 다만큼 많아지므로 중복된거까지 포함해서 페이징하여 제대로 된 페이징이 아니다?
     * 중복을 제거하고 페이징을 해야하는데 그렇게 하지 않으므로 원했던 결과가 안나옴
     * order기준이 아니라 다 기준으로 하니까
     * 참고: 컬렉션 페치 조인을 사용하면 페이징이 불가능하다. 하이버네이트는 경고 로그를 남기면서 모든
     * 데이터를 DB에서 읽어오고, 메모리에서 페이징 해버린다(매우 위험하다).
     * 참고: 컬렉션 페치 조인은 1개만 사용할 수 있다. 컬렉션 둘 이상에 페치 조인을 사용하면 안된다.
     * 데이터가 부정합하게 조회될 수 있다, 일대다의 다가 때문에 데이터가 완전 뻥튀기, 데이터를 못맞출수 이씀
     * @return
     */
    @GetMapping("/api/v3/orders")
    public List<OrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithItem();
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return result;
    }

    /**
     * 페이징 + 컬렉션 엔티티를 함께 조회
     * 먼저 ToOne(OneToOne, ManyToOne) 관계를 모두 페치조인 한다. ToOne 관계는 row수를
     * 증가시키지 않으므로 페이징 쿼리에 영향을 주지 않는다.
     * order입장에선 toOne관계 회원과 배송, 그래서 주문, 회원, 배송은 한방 쿼리로 가능
     * 컬렉션은 지연 로딩으로 조회한다.
     * 지연 로딩 성능 최적화를 위해 hibernate.default_batch_fetch_size , @BatchSize 를 적용
     * hibernate.default_batch_fetch_size는 글로벌 , @BatchSize는 특정 엔티티에
     * 보통N+1문제 터지면 하나씩 가져옴, hibernate.default_batch_fetch_size는 적어온 개수만큼 미리 가져옴
     *
     * toOne관계만 패치조인 하면 페이징 처리 가능
     * 1 M N 이 1 1 1로
     */

    /**
     * V3.1 엔티티를 조회해서 DTO로 변환 페이징 고려
     * - ToOne 관계만 우선 모두 페치 조인으로 최적화
     * - 컬렉션 관계는 hibernate.default_batch_fetch_size, @BatchSize로 최적화
     */
    @GetMapping("/api/v3.1/orders")
    //offset은 몇번째부터, limit은 개수제한
    //offset이 0이면 하이버네이트가 콘솔에서 지움
    public List<OrderDto> ordersV3_page(@RequestParam(value = "offset", defaultValue = "0") int offset,
                                        @RequestParam(value = "limit", defaultValue = "100") int limit) {

        /**
         * toOne관계인 주문, 회원, 배송만 패치조인한 메서드, 페이징 영향 주지 않음
         * user A와 B가 주문한거 다 가져옴, orders에 관련된 애들을 In쿼리로 날려서 가져옴
         * 1 n m이 1 1 1이 되는 최적화, 테이블 단위로 끊어 가져오므로 중복 없음, 정규화된 상태로
         *
         * 개별로 설정하려면 @BatchSize 를 적용하면 된다. (컬렉션은 컬렉션 필드에, 엔티티는 엔티티 클래스에 적용)
         * 장점
         * 쿼리 호출 수가 1 + N 1 + 1 로 최적화 된다.
         * 조인보다 DB 데이터 전송량이 최적화 된다. (Order와 OrderItem을 조인하면 Order가
         * OrderItem 만큼 중복해서 조회된다. 이 방법은 각각 조회하므로 전송해야할 중복 데이터가 없다.)
         * 페치 조인 방식과 비교해서 쿼리 호출 수가 약간 증가하지만, DB 데이터 전송량이 감소한다.
         * 컬렉션 페치 조인은 페이징이 불가능 하지만 이 방법은 페이징이 가능하다.
         * 결론
         * ToOne 관계는 페치 조인해도 페이징에 영향을 주지 않는다. 따라서 ToOne 관계는 페치조인으로
         * 쿼리 수를 줄이고 해결하고, 나머지는 hibernate.default_batch_fetch_size 로 최적화
         */
        List<Order> orders = orderRepository.findAllWithMemberDelivery(offset, limit);
        List<OrderDto> result = orders.stream()
                .map(o -> new OrderDto(o))
                .collect(toList());

        return result;
    }

    /**
     * Query: 루트 1번, 컬렉션 N 번 실행
     * ToOne(N:1, 1:1) 관계들을 먼저 조회하고, ToMany(1:N) 관계는 각각 별도로 처리한다.
     * 이런 방식을 선택한 이유는 다음과 같다.
     * ToOne 관계는 조인해도 데이터 row 수가 증가하지 않는다.
     * ToMany(1:N) 관계는 조인하면 row 수가 증가한다.
     * row 수가 증가하지 않는 ToOne 관계는 조인으로 최적화 하기 쉬우므로 한번에 조회하고, ToMany
     * 관계는 최적화 하기 어려우므로 findOrderItems() 같은 별도의 메서드로 조회한다
     * @return
     */
    @GetMapping("/api/v4/orders")
    public List<OrderQueryDto> ordersV4() {

        return orderQueryRepository.findOrderQueryDtos();
    }

    /**
     * Query: 루트 1번, 컬렉션 1번
     * ToOne 관계들을 먼저 조회하고, 여기서 얻은 식별자 orderId로 ToMany 관계인 OrderItem 을
     * 한꺼번에 조회
     * MAP을 사용해서 매칭 성능 향상(O(1))
     * @return
     */
    @GetMapping("/api/v5/orders")
    public List<OrderQueryDto> ordersV5() {

        return orderQueryRepository.findAllByDto_optimization();
    }

    /**
     * V6는 완전히 다른 접근방식이다. 쿼리 한번으로 최적화 되어서 상당히 좋아보이지만, Order를 기준으로
     * 페이징이 불가능하다. 실무에서는 이정도 데이터면 수백이나, 수천건 단위로 페이징 처리가 꼭 필요하므로,
     * 이 경우 선택하기 어려운 방법이다. 그리고 데이터가 많으면 중복 전송이 증가해서 V5와 비교해서 성능차이도 미비
     * 네트워크 이동이 줄어듬, v5는 쿼리 2방 나가므로 네트워크 2번 호출
     * Query: 1번
     *
     * 단점
     * 쿼리는 한번이지만 조인으로 인해 DB에서 애플리케이션에 전달하는 데이터에 중복 데이터가 추가되므로
     *  상황에 따라 V5 보다 더 느릴 수 도 있다.
     * 애플리케이션에서 추가 작업이 크다. 분해해야 하는 경우
     * 페이징 불가능, order를 기준으로 할때는 안됨됨    @GetMapping("/api/v6/orders")
     * OrderFlatDto가 아닌 OrderQueryDto로 스펙을 맞추고 싶으면 직접 중복을 걸르면 됨
     * 루프를 돌려 OrderQueryDto랑 OrderItemQueryDto 발라내면 됨
     * @return
     */
    public List<OrderQueryDto> ordersV6() {
        List<OrderFlatDto> flats = orderQueryRepository.findAllByDto_flat();

        /**
         * flats을 가지고 루프를 돌려 OrderFlatDto를 OrderQueryDto로 바꾸는
         * 그러면서 OrderQueryDto랑 OrderItemQueryDto 관련된걸 발라내 최종적으로 OrderQueryDto 만듬
         * 그룹바이할때 뭘 묶을지 알려줘야함, 아이디를 알려줌
         * OrderQueryDto클래스에서 해시코드 넣어줌
         */
        return flats.stream()
                .collect(groupingBy(o -> new OrderQueryDto(o.getOrderId(), o.getName(), o.getOrderDate(), o.getOrderStatus(), o.getAddress()),
                        mapping(o -> new OrderItemQueryDto(o.getOrderId(), o.getItemName(), o.getOrderPrice(), o.getCount()), toList())
                )).entrySet().stream()
                .map(e -> new OrderQueryDto(e.getKey().getOrderId(), e.getKey().getName(), e.getKey().getOrderDate(), e.getKey().getOrderStatus(), e.getKey().getAddress(), e.getValue()))
                .collect(toList());
    }


    /**
     * v6가 가장 좋다고 말하기 어렵다. v5는 쿼리는 2번 정규화된 데이터 v6는 쿼리가 한 번 나가지만 많은 데이터
     */
    @Data
    static class OrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate; //주문시간
        private OrderStatus orderStatus;
        //이런 valueObject는 노출해도 됨
        private Address address;

        /**
         * 현재는 없지만 DTO안에 엔티티가 있으면 안됨, 래핑해도 안됨
         * 외부에 노출됨, 엔티티에 대한 의존을 완전히 끊어야됨
         * 따로 DTO클래스 만들어서 래핑
         */
        private List<OrderItemDto> orderItems;

        public OrderDto(Order order) {
            orderId = order.getId();
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            address = order.getDelivery().getAddress();
            //또 엔티티가 나왔기 때문에 orderItems도 OrderItemDto로 변환, 프록시 초기화
            orderItems = order.getOrderItems().stream()
                    .map(orderItem -> new OrderItemDto(orderItem))
                    .collect(toList());
        }
    }

    @Data
    static class OrderItemDto {
        /**
         * API를 사용하는 클라이언트 입장에서는 이 3개만 있어도 됨
         * 이렇게하면 주문한 아이템안에 각각 아이템 정보에 이 3개만
         */
        private String itemName;//상품 명
        private int orderPrice; //주문 가격
        private int count;      //주문 수량

        public OrderItemDto(OrderItem orderItem) {
            itemName = orderItem.getItem().getName();
            orderPrice = orderItem.getOrderPrice();
            count = orderItem.getCount();
        }
    }

}
