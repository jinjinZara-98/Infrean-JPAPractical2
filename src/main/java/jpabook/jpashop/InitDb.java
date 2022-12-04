package jpabook.jpashop;

import jpabook.jpashop.domain.*;
import jpabook.jpashop.domain.item.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.persistence.EntityManager;

//샘플 데이터 넣는 클래스
/**
 * 종 주문 2개
 *
 * * userA
 * 	 * JPA1 BOOK
 * 	 * JPA2 BOOK
 *
 * * userB
 * 	 * SPRING1 BOOK
 * 	 * SPRING2 BOOK
 */
//컴포넌트스캔 대상
@Component
@RequiredArgsConstructor
public class InitDb {

    //@RequiredArgsConstructor로 생성자 생성, 생성자 하나면 @Autowired 기본으로 붙여줘 자동 의존 주입
    private final InitService initService;

    //서버가 뜰 때 스프링이 엮이고 나면
    //어플리케이션 로딩 시점에 호출, 스프링 빈이 올라오고 나면 스프링이 호출
    //ddl-auto를 create로 하고 실행하면 실행할때 값을 다 비우고 밑에 값들만 추가
    @PostConstruct
    public void init() {
        initService.dbInit1();
        initService.dbInit2();
    }

    @Component
    @Transactional
    @RequiredArgsConstructor
    static class InitService {

        private final EntityManager em;

        public void dbInit1() {
            System.out.println("Init1" + this.getClass());
            Member member = createMember("userA", "서울", "1", "1111");
            em.persist(member);

            Book book1 = createBook("JPA1 BOOK", 10000, 100);
            em.persist(book1);

            Book book2 = createBook("JPA2 BOOK", 20000, 100);
            em.persist(book2);

            //고객이 주문한 아이템
            OrderItem orderItem1 = OrderItem.createOrderItem(book1, 10000, 1);
            OrderItem orderItem2 = OrderItem.createOrderItem(book2, 20000, 2);

            //배송 객체 생성해 어떤 회원이 주문했는지 넣고
            Delivery delivery = createDelivery(member);
            //주문 객체 생성해 회원, 배송, 주문한 상품들 넣음
            Order order = Order.createOrder(member, delivery, orderItem1, orderItem2);
            em.persist(order);
        }

        public void dbInit2() {
            Member member = createMember("userB", "진주", "2", "2222");
            em.persist(member);

            Book book1 = createBook("SPRING1 BOOK", 20000, 200);
            em.persist(book1);

            Book book2 = createBook("SPRING2 BOOK", 40000, 300);
            em.persist(book2);

            OrderItem orderItem1 = OrderItem.createOrderItem(book1, 20000, 3);
            OrderItem orderItem2 = OrderItem.createOrderItem(book2, 40000, 4);

            Delivery delivery = createDelivery(member);
            Order order = Order.createOrder(member, delivery, orderItem1, orderItem2);
            em.persist(order);
        }

        //DB에 넣을 객체 세팅하는 메서드
        private Member createMember(String name, String city, String street, String zipcode) {
            //파라미터로 들어온 값들 세팅 후 Member객체 반환
            Member member = new Member();
            member.setName(name);
            member.setAddress(new Address(city, street, zipcode));

            return member;
        }

        private Book createBook(String name, int price, int stockQuantity) {
            Book book1 = new Book();
            book1.setName(name);
            book1.setPrice(price);
            book1.setStockQuantity(stockQuantity);
            return book1;
        }

        private Delivery createDelivery(Member member) {
            Delivery delivery = new Delivery();
            delivery.setAddress(member.getAddress());
            return delivery;
        }
    }
}

