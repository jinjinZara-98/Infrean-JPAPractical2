package jpabook.jpashop.api;

import jpabook.jpashop.domain.Address;
import jpabook.jpashop.domain.Order;
import jpabook.jpashop.domain.OrderStatus;
import jpabook.jpashop.repository.*;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto;
import jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

import static java.util.stream.Collectors.toList;

//주문과 관련되 간단한 레스트 API
/**
 *
 * xToOne(ManyToOne, OneToOne) 관계 최적화
 * Order
 * Order -> Member
 * Order -> Delivery
 *
 */
@RestController
@RequiredArgsConstructor
public class OrderSimpleApiController {

    //@RequiredArgsConstructor로 자동 으존 주입
    private final OrderRepository orderRepository;
    private final OrderSimpleQueryRepository orderSimpleQueryRepository; //의존관계 주입

    /**
     * V1. 엔티티 직접 노출
     * - Hibernate5Module 모듈 등록, LAZY=null 처리
     * - 양방향 관계 문제 발생 -> @JsonIgnore
     */

    /**
     * 무한 루프 발생, 양방향 걸리는 둘중에 하나는 ignore해줘야함
     * order클래스의 Member가 지연로딩이므로 DB에서 안끌고옴 order객체만 갖고옴
     * 그래서 Member는 가짜 프록시 Member객체를 넣어둠
     * Member객체에 손대면 db에서 Member객체를 가져옴
     * 이렇게 지연로딩인 경우에는 하이버네이트다 제이슨 라이브러리야 아무것도 하지마라 할 수 있음
     * 스프링 메인 클래스에 등록함
     * 필요한건 쥬뮨, 회원, 배송인데 배송된 아이템까지 갖고옴
     * 필요없는 쿼리까지 실행해 느려진다 성능 문제?
     * @return
     */
    @GetMapping("/api/v1/simple-orders")
    public List<Order> ordersV1() {

        //검색 조건이 없기 때문에 주문을 다 들고 옴
        List<Order> all = orderRepository.findAllByString(new OrderSearch());

        /**
         * 원하는 애만 골라 출력, 루프를 돌리며 강제로 지연로딩
         * order.getMember() 여기까지는 프록시 객체
         * getName()하면 실제 객체 갖고옴, 지연로딩이 강제 초기화
         * 이렇게하면 회워과 배송만 나오게 됨
         * 초기화 안된거 안보내고, 초기화 된거만 보냄
         * 이래도 아직까지 필요없는거까지 노출
         * 지연 로딩(LAZY)을 피하기 위해 즉시 로딩(EARGR)으로 설정하면 안된다! 즉시 로딩 때문에
         * 연관관계가 필요 없는 경우에도 데이터를 항상 조회해서 성능 문제가 발생할 수 있다. 즉시 로딩으로
         * 설정하면 성능 튜닝이 매우 어려워 진다.
         * 항상 지연 로딩을 기본으로 하고, 성능 최적화가 필요한 경우에는 페치 조인(fetch join)을 사용
         */
        for (Order order : all) {
            order.getMember().getName(); //Lazy 강제 초기화
            order.getDelivery().getAddress(); //Lazy 강제 초기화
        }
        return all;
    }

    /**
     * V2. 엔티티를 조회해서 DTO로 변환(fetch join 사용X)
     * - 단점: 지연로딩으로 쿼리 N번 호출
     */
    //이것 또한 지연로딩으로 인해 많은 쿼리가 호출됨
    @GetMapping("/api/v2/simple-orders")
    public List<SimpleOrderDto> ordersV2() {
        List<Order> orders = orderRepository.findAll();

        /**
         * 엔티티를 그대로 반환하지 않게
         * List<Order>를 List<SimpleOrderDto> 로 바꾸는
         * 결과 주문수가 2개면 루프 2번 돔
         *
         * SimpleOrderDto 코드에서
         * 첫번째 루프에서 주문 조회 쿼리, 그리고 order.getMember().getName();로 회원을 찾음, 회원 찾는 쿼리 나감
         * order.getDelivery().getAddress()로 배송 쿼리 나감, 배송을 하나 조회
         * 이후 다음 두번째 루프에서 똑같이 회원과 배송 조회, 총 5번 쿼리 나감
         * 첫번째 3번 두번째 2번, N+1번 문제
         */
        List<SimpleOrderDto> result = orders.stream()
                /**
                 * new SimpleOrderDto(o)) 생성하면서 get메서드 쓸때 지연로딩 초기화
                 * 그 객체를 통해 메서드 사용해 직접 사용하므로
                 */
                .map(o -> new SimpleOrderDto(o))
                .collect(toList());

        return result;
    }

    /**
     * V3. 엔티티를 조회해서 DTO로 변환(fetch join 사용O)
     * - fetch join으로 쿼리 1번 호출
     * 참고: fetch join에 대한 자세한 내용은 JPA 기본편 참고(정말 중요함)
     */

    /**
     * v2에서 5번의 쿼리 대신 쿼리 1번 나감, 행이 길게 나오고 JPA가 적당히 잘라 연관관계 셋팅해 넣어줌
     * 페치 조인으로 order -> member , order -> delivery 는 이미 조회 된 상태 이므로 지연로딩X
     * 단점 select에서 엔티티를 찍어서 조회하는것
     * 다른 API에서 사용가능, 재사용성 높음
     * @return
     */
    @GetMapping("/api/v3/simple-orders")
    public List<SimpleOrderDto> ordersV3() {
        List<Order> orders = orderRepository.findAllWithMemberDelivery();
        List<SimpleOrderDto> result = orders.stream()
                .map(o -> new SimpleOrderDto(o))
                .collect(toList());
        return result;
    }

    /**
     * v3처럼 엔티티를 dto로 변환하는게 아닌 dto로 바로 끄집어내는
     * select에서 원하는거만 뽑아냄
     * Repository는 엔티티 조회하는데 써야함, 엔티티 객체 그래프 조회하는거 최적화하거나
     * v3보다 성능 최적화는 좋지만 재사용성면에서 단점
     * 리포지토리가 화면에 의존, API스펙이 바뀌면 DTO를 고쳐야함
     * @return
     */
    @GetMapping("/api/v4/simple-orders")
    public List<OrderSimpleQueryDto> ordersV4() {
        return orderSimpleQueryRepository.findOrderDtos();
    }

    /**
     * v3 v4는 서로 우위를 가리기 힘듬
     *
     * 엔티티를 DTO로 변환하거나, DTO로 바로 조회하는 두가지 방법은 각각 장단점이 있다. 둘중 상황에
     * 따라서 더 나은 방법을 선택하면 된다. 엔티티로 조회하면 리포지토리 재사용성도 좋고, 개발도 단순해진다.
     * 따라서 권장하는 방법은 다음과 같다.
     * 쿼리 방식 선택 권장 순서
     * 1. 우선 엔티티를 DTO로 변환하는 방법을 선택한다.
     * 2. 필요하면 페치 조인으로 성능을 최적화 한다. 대부분의 성능 이슈가 해결된다.
     * 3. 그래도 안되면 DTO로 직접 조회하는 방법을 사용한다.
     * 4. 최후의 방법은 JPA가 제공하는 네이티브 SQL이나 스프링 JDBC Template을 사용해서 SQL을 직접 사용
     *
     * API스펙 명확하게 규정
     * 클래스 필드 변경해도 컴파일오류로 캐치가능
     */
    @Data
    static class SimpleOrderDto {

        private Long orderId;
        private String name;
        private LocalDateTime orderDate; //주문시간
        private OrderStatus orderStatus;
        private Address address;

        public SimpleOrderDto(Order order) {
            orderId = order.getId();
            //이때 lazy 초기화 되는
            //영컨이 이 memberid 가지고 찾아서 없으면 db쿼리 날리는
            name = order.getMember().getName();
            orderDate = order.getOrderDate();
            orderStatus = order.getStatus();
            //이때 lazy 초기화 되는
            address = order.getDelivery().getAddress();
        }
    }
}
