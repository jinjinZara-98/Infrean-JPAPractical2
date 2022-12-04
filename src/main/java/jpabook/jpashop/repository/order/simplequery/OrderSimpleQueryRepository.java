package jpabook.jpashop.repository.order.simplequery;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.persistence.EntityManager;
import java.util.List;

//별도로 뽑아내 유지보수성 좋음, 오직 엔티티를 조회하는 리포지토리가 아니므로
@Repository
@RequiredArgsConstructor
public class OrderSimpleQueryRepository {

    private final EntityManager em;

    //쿼리를 할때 sql하듯이 jpql로 짜서 가져옴, 화면에는 최적화 재사용성 낮음
    //핏하게 만들어서 로직을 재활용하기 어려움, 성능 최적화는 좀 더 나음 v3보다
    //일반적인 SQL을 사용할 때 처럼 원하는 값을 선택해서 조회
    //new 명령어를 사용해서 JPQL의 결과를 DTO로 즉시 변환
    //SELECT 절에서 원하는 데이터를 직접 선택하므로 DB 애플리케이션 네트웍 용량 최적화
    //리포지토리 재사용성 떨어짐, API 스펙에 맞춘 코드가 리포지토리에 들어가는 단점
    //JPA는 엔티티나 가본값을 반환할 수 있음, DTO는 New Operation사용해야함
    //OrderSimpleQueryDto.class는 반환타입
    public List<OrderSimpleQueryDto> findOrderDtos() {
        return em.createQuery(
                "select new jpabook.jpashop.repository.order.simplequery.OrderSimpleQueryDto(o.id, m.name, o.orderDate, o.status, d.address)" +
                        " from Order o" +
                        " join o.member m" +
                        " join o.delivery d", OrderSimpleQueryDto.class)
                .getResultList();
    }
}